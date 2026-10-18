param(
    [ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version = '0.1.13',
    [string]$Serial = 'emulator-5580',
    [ValidateSet('phone','tablet7','tablet10')][string]$Device = 'phone',
    [ValidateSet('en-US','tr-TR')][string[]]$Locales = @('en-US'),
    [ValidateSet('home','image','video','audio','pdf','documents','presentation','batch')][string[]]$Scenes = @('home','image','video','audio','pdf','documents','presentation','batch'),
    [switch]$SkipHarnessBuild,
    [switch]$KeepQaData
)
$ErrorActionPreference = 'Stop'
$captureRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$captureAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function Adb {
    & $captureAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "Screenshot QA command failed: $args" }
}
if ($Serial -notmatch '^emulator-\d+$' -or ((Adb shell getprop ro.kernel.qemu) -join '').Trim() -ne '1' -or
    (Adb emu avd name)[0].Trim() -ne 'ofice_qa') { throw 'Only the dedicated ofice_qa emulator may be used/reset.' }
$captureOutput = Join-Path $captureRoot "output/play-store/$Version"
$captureInputs = Join-Path $captureOutput 'inputs'
$captureWidth = 1080
$captureHeight = 1920
$captureDensity = 420
$captureDevicePath = ''
if ($Device -eq 'tablet7') { $captureWidth = 1200; $captureDensity = 320; $captureDevicePath = 'tablets/tablet-7-inch' }
if ($Device -eq 'tablet10') { $captureWidth = 1600; $captureHeight = 2560; $captureDensity = 320; $captureDevicePath = 'tablets/tablet-10-inch' }
$captureInspection = Get-Content (Join-Path $captureRoot "output/releases/$Version/inspection.json") -Raw | ConvertFrom-Json
if ($captureInspection.versionName -ne $Version -or $captureInspection.versionCode -ne 14) { throw 'Captures require frozen 0.1.13/code 14.' }
$captureApk = Join-Path $captureRoot "output/development/$Version/birlevo-$Version-14-release.apk"
$captureRecords = Get-Content (Join-Path $captureRoot "output/releases/$Version/artifacts.json") -Raw | ConvertFrom-Json
if ((Get-FileHash $captureApk).Hash.ToLowerInvariant() -ne $captureRecords."birlevo-$Version-14-release.apk".sha256) { throw 'Frozen APK hash mismatch.' }
foreach ($captureInput in @('lake.png','cover.png','lake-video.mp4','lake-melody.wav','travel-budget.csv')) {
    if (!(Test-Path (Join-Path $captureInputs $captureInput))) { throw "Missing owned synthetic fixture: $captureInput" }
}
New-Item -ItemType Directory -Force (Join-Path $captureOutput 'logs') | Out-Null
Push-Location $captureRoot
try {
    if (!$SkipHarnessBuild) {
        & ./gradlew.bat --no-daemon assembleDebugAndroidTest '-Pbirlevo.storeCapture=true' --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Independent capture harness build failed.' }
    }
    $captureTestApk = Join-Path $captureOutput 'store-capture-test.apk'
    $captureSigning = Join-Path $env:LOCALAPPDATA 'Birlevo/signing'
    $captureCredential = Import-Clixml (Join-Path $captureSigning 'upload-credential.clixml')
    $captureOldPassword = [Environment]::GetEnvironmentVariable('BIRLEVO_CAPTURE_KEY_PASSWORD')
    try {
        $env:BIRLEVO_CAPTURE_KEY_PASSWORD = $captureCredential.GetNetworkCredential().Password
        & (Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.0.0/apksigner.bat') sign --ks (Join-Path $captureSigning 'birlevo-upload.p12') --ks-key-alias $captureCredential.UserName --ks-pass env:BIRLEVO_CAPTURE_KEY_PASSWORD --key-pass env:BIRLEVO_CAPTURE_KEY_PASSWORD --out $captureTestApk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw 'Could not sign the independent capture harness.' }
    } finally {
        [Environment]::SetEnvironmentVariable('BIRLEVO_CAPTURE_KEY_PASSWORD', $captureOldPassword)
        $captureCredential.Password.Dispose()
    }
    Adb install -r $captureApk
    Adb install -r $captureTestApk
    Adb shell mkdir -p /sdcard/Android/data/studio.birlevo.android.test/files/store-capture
    foreach ($captureInput in @('lake.png','cover.png','lake-video.mp4','lake-melody.wav','travel-budget.csv')) {
        Adb push (Join-Path $captureInputs $captureInput) "/sdcard/Android/data/studio.birlevo.android.test/files/store-capture/$captureInput"
    }
    Adb shell wm size "${captureWidth}x${captureHeight}"
    Adb shell wm density $captureDensity
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Adb shell settings put global sysui_demo_allowed 1
    Adb shell am broadcast -a com.android.systemui.demo --es command enter | Out-Null
    Adb shell am broadcast -a com.android.systemui.demo --es command clock --es hhmm 1000 | Out-Null
    Adb shell am broadcast -a com.android.systemui.demo --es command battery --es level 100 --es plugged false | Out-Null
    Adb shell am broadcast -a com.android.systemui.demo --es command notifications --es visible false | Out-Null
    Add-Type -AssemblyName System.Drawing
    $captureOrder = @('home','image','video','audio','pdf','documents','presentation','batch')
    foreach ($captureLocale in $Locales) {
        if (!$KeepQaData) { Adb shell pm clear studio.birlevo.android | Out-Null }
        Adb shell pm grant studio.birlevo.android android.permission.POST_NOTIFICATIONS
        $captureFolder = Join-Path (Join-Path $captureOutput $captureDevicePath) $captureLocale
        $captureRawFolder = Join-Path (Join-Path $captureOutput "raw/$captureDevicePath") $captureLocale
        New-Item -ItemType Directory -Force $captureFolder,$captureRawFolder | Out-Null
        foreach ($captureScene in $Scenes) {
            $captureLogName = if ($Device -eq 'phone') { "$captureLocale-$captureScene.log" } else { "$Device-$captureLocale-$captureScene.log" }
            $captureLog = Join-Path $captureOutput "logs/$captureLogName"
            if (Test-Path $captureLog) { Copy-Item $captureLog "$captureLog.$([DateTime]::UtcNow.ToString('yyyyMMddHHmmss')).previous" }
            Adb shell timeout 180 am instrument -w -r -e locale $captureLocale -e scene $captureScene -e device $Device studio.birlevo.android.test/com.example.contentstudio.StoreCaptureInstrumentation *> $captureLog
            $captureResult = Get-Content $captureLog -Raw
            if ($captureResult -notmatch "OK \(1 store $captureScene $captureLocale capture\)" -or
                $captureResult -notmatch 'INSTRUMENTATION_CODE: -1') { throw "Screenshot scene failed: $captureLocale/$captureScene; see $captureLog" }
            if ($captureResult -notmatch "Profile ${Device}: ${captureWidth}x${captureHeight}") { throw 'Capture device profile mismatch.' }
            $captureRaw = Join-Path $captureRawFolder "$captureScene.png"
            Adb pull "/sdcard/Android/data/studio.birlevo.android/files/store-captures/$captureLocale/$captureScene.png" $captureRaw
            $captureName = '{0:D2}-{1}.png' -f ($captureOrder.IndexOf($captureScene)+1),$captureScene
            $captureImage = [System.Drawing.Image]::FromFile($captureRaw)
            $captureRgb = [System.Drawing.Bitmap]::new($captureWidth,$captureHeight,[System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
            $captureGraphics = [System.Drawing.Graphics]::FromImage($captureRgb)
            try {
                if ($captureImage.Width -ne $captureWidth -or $captureImage.Height -ne $captureHeight) { throw 'Unexpected raw screenshot dimensions.' }
                $captureGraphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $captureGraphics.DrawImageUnscaled($captureImage,0,0)
                $captureRgb.Save((Join-Path $captureFolder $captureName),[System.Drawing.Imaging.ImageFormat]::Png)
            } finally { $captureGraphics.Dispose(); $captureRgb.Dispose(); $captureImage.Dispose() }
            Write-Output "Captured signed 0.1.13 UI: $Device/$captureLocale/$captureName"
        }
    }
} finally {
    # Restore the dedicated QA emulator's normal display after success or a failed scene.
    & $captureAdb -s $Serial shell am broadcast -a com.android.systemui.demo --es command exit | Out-Null
    & $captureAdb -s $Serial shell settings put global sysui_demo_allowed 0 | Out-Null
    & $captureAdb -s $Serial shell wm size reset | Out-Null
    & $captureAdb -s $Serial shell wm density reset | Out-Null
    & $captureAdb -s $Serial shell settings put system font_scale 1.0 | Out-Null
    & $captureAdb -s $Serial shell cmd uimode night no | Out-Null
    Pop-Location
}
