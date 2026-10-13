param([string]$Serial = 'emulator-5554', [switch]$SkipBuild, [switch]$SkipLayouts, [switch]$OnlyEdits)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/audio-edits'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa\s*$') { throw 'Only the ofice_qa emulator is allowed.' }
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain 2>&1 |
            Out-File (Join-Path $reportRoot 'build.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug checks failed; inspect build.log.' }
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    $log = Join-Path $reportRoot $(if ($OnlyEdits) { 'device-edits.log' } else { 'device-all.log' })
    $classes = if ($OnlyEdits) { 'com.example.contentstudio.audio.AudioEditDeviceTest,com.example.contentstudio.AudioEditUiDeviceTest,com.example.contentstudio.AudioToolDeviceTest#editedVideoTransfersItsActualTrimmedBytesIntoAudioToolWithoutReencodingTheHandoff' }
        else { 'com.example.contentstudio.AudioToolDeviceTest,com.example.contentstudio.AudioTargetsDeviceTest,com.example.contentstudio.audio.Mp3DeviceTest,com.example.contentstudio.audio.AudioEditDeviceTest,com.example.contentstudio.AudioEditUiDeviceTest' }
    $count = if ($OnlyEdits) { 8 } else { 29 }
    Adb shell am instrument -w -r -e class $classes `
        studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $log -Encoding utf8
    if ((Get-Content $log -Raw) -notmatch "OK \($count tests\)") { throw 'Audio checks failed; inspect device-all.log.' }
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/audio-edits (Join-Path $reportRoot 'files')
    & python tools/testing/verify_audio_edit_files.py (Join-Path $reportRoot 'files') --report (Join-Path $reportRoot 'independent-decode.json')
    if ($LASTEXITCODE -ne 0) { throw 'Independent decode failed.' }
    if (-not $SkipLayouts) {
        foreach ($mode in @('tablet', 'narrow-large-text', 'dark')) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            $layoutLog = Join-Path $reportRoot "$mode.log"
            Adb shell am instrument -w -r -e class com.example.contentstudio.AudioEditUiDeviceTest `
                studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $layoutLog -Encoding utf8
            if ((Get-Content $layoutLog -Raw) -notmatch 'OK \(1 test\)') { throw "Layout verification failed: $mode" }
            foreach ($name in @('sources', 'range', 'result')) {
                Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/audio-edit-$name.png" (Join-Path $reportRoot "$mode-$name.png")
            }
        }
    }
    Write-Output 'Audio editing QA passed. No version bump or release built.'
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Pop-Location
}
