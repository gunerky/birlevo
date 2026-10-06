param([string]$Version = '0.1.7', [string]$Serial = 'emulator-5554',
    [ValidateSet('phone', 'tablet', 'narrow-large-text', 'dark')][string[]]$Modes = @('tablet', 'narrow-large-text', 'dark'),
    [switch]$SkipInitialRestore, [switch]$PdfOnly,
    [ValidateSet('image','video','pdf','transfer','formats','photos','support')][string[]]$Workflows)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$qaSigner = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\36.0.0\apksigner.bat'
function Adb {
    & $qaAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "QA command failed: $args" }
}
if ($Serial -notmatch '^emulator-\d+$' -or ((Adb shell getprop ro.kernel.qemu) -join '').Trim() -ne '1' -or
    (Adb emu avd name)[0].Trim() -ne 'ofice_qa') { throw 'Only the ofice_qa emulator may be used.' }
$qaRoot = Join-Path $projectRoot "output\development\$Version\release-smoke"
New-Item -ItemType Directory -Force $qaRoot | Out-Null
$installedPath = ((Adb shell pm path studio.birlevo.android) -join '').Trim().Substring(8)
Adb pull $installedPath (Join-Path $qaRoot 'installed-release.apk') | Out-Null
$frozenInspection = Get-Content -LiteralPath (Join-Path $projectRoot "output\releases\$Version\inspection.json") -Raw | ConvertFrom-Json
if ($frozenInspection.versionName -ne $Version) { throw 'Frozen release metadata version mismatch.' }
$expectedApk = Join-Path $projectRoot "output\development\$Version\birlevo-$Version-$($frozenInspection.versionCode)-release.apk"
if ((Get-FileHash $expectedApk).Hash -ne (Get-FileHash (Join-Path $qaRoot 'installed-release.apk')).Hash) {
    throw 'The installed APK is not the frozen signed release APK.'
}
# Preserve the phone harness which already passed before rebuilding the updated async-intent wait.
Copy-Item -LiteralPath (Join-Path $qaRoot 'release-signed-test.apk') -Destination (Join-Path $qaRoot 'phone-signed-test.apk') -Force
Push-Location $projectRoot
try {
    & .\gradlew.bat assembleDebugAndroidTest '-Pbirlevo.platformSmoke=true' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Harness build failed.' }
} finally { Pop-Location }
$qaApk = Join-Path $qaRoot 'layouts-signed-test.apk'
$qaSigning = Join-Path $env:LOCALAPPDATA 'Birlevo\signing'
$qaCredential = Import-Clixml -LiteralPath (Join-Path $qaSigning 'upload-credential.clixml')
$oldPassword = [Environment]::GetEnvironmentVariable('BIRLEVO_QA_KEY_PASSWORD')
try {
    $env:BIRLEVO_QA_KEY_PASSWORD = $qaCredential.GetNetworkCredential().Password
    & $qaSigner sign --ks (Join-Path $qaSigning 'birlevo-upload.p12') --ks-key-alias $qaCredential.UserName `
        --ks-pass env:BIRLEVO_QA_KEY_PASSWORD --key-pass env:BIRLEVO_QA_KEY_PASSWORD --out $qaApk `
        (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Harness signing failed.' }
} finally {
    [Environment]::SetEnvironmentVariable('BIRLEVO_QA_KEY_PASSWORD', $oldPassword)
    $qaCredential.Password.Dispose()
}
Adb install -r $qaApk
function RestoreProject([string]$Folder) {
    Adb shell am force-stop studio.birlevo.android
    Adb shell timeout 90 am instrument -w -r -e restoreOnly true studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $Folder 'process-restore.log')
    if ((Get-Content (Join-Path $Folder 'process-restore.log') -Raw) -notmatch 'OK \(1 restored project workflow\)') {
        throw "Process restart/owned asset failed: $Folder"
    }
}
try {
    if (!$SkipInitialRestore) {
        RestoreProject $qaRoot
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $qaRoot | Out-Null
    }
    foreach ($qaMode in $Modes) {
        $qaFolder = Join-Path $qaRoot $qaMode
        New-Item -ItemType Directory -Force $qaFolder | Out-Null
        Adb shell am force-stop studio.birlevo.android
        switch ($qaMode) {
            'phone' { Adb shell wm size reset; Adb shell wm density reset; Adb shell settings put system font_scale 1.0; Adb shell cmd uimode night no }
            'tablet' { Adb shell wm size 1600x2560; Adb shell wm density 240; Adb shell settings put system font_scale 1.0; Adb shell cmd uimode night no }
            'narrow-large-text' { Adb shell wm size 720x1440; Adb shell wm density 360; Adb shell settings put system font_scale 2.0; Adb shell cmd uimode night no }
            'dark' { Adb shell wm size reset; Adb shell wm density reset; Adb shell settings put system font_scale 1.0; Adb shell cmd uimode night yes }
        }
        if ($Workflows) {
            foreach ($qaWorkflow in $Workflows) {
                Adb shell timeout 240 am instrument -w -r -e workflow $qaWorkflow studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $qaFolder "$qaWorkflow.log")
                if ((Get-Content (Join-Path $qaFolder "$qaWorkflow.log") -Raw) -notmatch "OK \(1 $qaWorkflow workflow\)") {
                    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $qaFolder | Out-Null
                    throw "Release workflow failed: $qaMode/$qaWorkflow"
                }
            }
            if ($Workflows -contains 'transfer') { RestoreProject $qaFolder }
            Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $qaFolder | Out-Null
            Write-Output "Release workflows passed: $qaMode/$($Workflows -join ',')"
            continue
        } elseif ($PdfOnly) {
            Adb shell timeout 120 am instrument -w -r -e pdfOnly true studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $qaFolder 'instrumentation.log')
            $expectedResult = 'OK \(1 PDF workflow\)'
        } else {
            Adb shell timeout 600 am instrument -w -r studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $qaFolder 'instrumentation.log')
            $workflowCount = if ($frozenInspection.versionCode -ge 9) { 7 } elseif ($frozenInspection.versionCode -ge 8) { 6 } else { 4 }
            $expectedResult = "OK \($workflowCount release workflows\)"
        }
        if ((Get-Content (Join-Path $qaFolder 'instrumentation.log') -Raw) -notmatch $expectedResult) {
            Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $qaFolder | Out-Null
            throw "Release output layout failed: $qaMode"
        }
        if (!$PdfOnly) { RestoreProject $qaFolder }
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $qaFolder | Out-Null
        if ($PdfOnly) { Write-Output "PDF generate/open/share diagnostic passed: $qaMode" }
        else { Write-Output "Output workflows, read grants, larger-copy approval and process restoration passed: $qaMode" }
    }
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
}
