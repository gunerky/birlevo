param([string]$Serial = 'emulator-5554', [switch]$SkipBuild, [switch]$SkipLayouts, [switch]$OnlyWindow, [switch]$OnlyLayouts)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot "output/development/video-edits/$Serial"
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb {
    # Display overrides can briefly reconnect the emulator after instrumentation exits.
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        $deviceState = & $qaAdb -s $Serial get-state 2>$null
        if ($deviceState -eq 'device') { break }
        Start-Sleep -Milliseconds 500
    }
    & $qaAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
}
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated ofice QA emulators are allowed.' }
function CheckDevice([string]$Classes, [int]$Count, [string]$Name) {
    $log = Join-Path $reportRoot "$Name.log"
    Adb shell am instrument -w -r -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner |
        Out-File $log -Encoding utf8
    if ((Get-Content $log -Raw) -notmatch "OK \($Count tests?\)") { throw "Device check failed: $log" }
}
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain 2>&1 |
            Out-File (Join-Path $reportRoot 'build.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug checks failed; inspect build.log.' }
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if (-not $OnlyLayouts -and $OnlyWindow) { CheckDevice 'com.example.contentstudio.SystemBarsDeviceTest' 1 'window' }
    elseif (-not $OnlyLayouts) {
        CheckDevice 'com.example.contentstudio.VideoEditDeviceTest,com.example.contentstudio.VideoEditUiDeviceTest,com.example.contentstudio.VideoOutputIntegrationDeviceTest,com.example.contentstudio.VideoCompressionDeviceTest,com.example.contentstudio.MediaSessionDeviceTest,com.example.contentstudio.SystemBarsDeviceTest' 30 'device-all'
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-edits (Join-Path $reportRoot 'files')
        & python tools/testing/verify_video_edit_files.py (Join-Path $reportRoot 'files') --report (Join-Path $reportRoot 'independent-decode.json')
        if ($LASTEXITCODE -ne 0) { throw 'Independent frame / audio decode failed.' }
    }
    if (-not $SkipLayouts) {
        foreach ($mode in @('tablet', 'narrow-large-text', 'dark')) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            $classes = if ($OnlyWindow) { 'com.example.contentstudio.SystemBarsDeviceTest' }
                else { 'com.example.contentstudio.SystemBarsDeviceTest,com.example.contentstudio.VideoEditUiDeviceTest' }
            CheckDevice $classes $(if ($OnlyWindow) { 1 } else { 2 }) $mode
            Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/system-bars.png (Join-Path $reportRoot "$mode-window.png")
            if (-not $OnlyWindow) {
                foreach ($name in @('sources', 'options', 'result', 'player')) {
                    Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/video-edit-$name.png" (Join-Path $reportRoot "$mode-$name.png")
                }
            }
        }
    }
    Write-Output 'Video editing / window QA passed. No release build or version bump.'
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Pop-Location
}
