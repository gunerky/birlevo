param([string]$Serial = 'emulator-5554', [switch]$SkipBuild, [switch]$SkipLayouts, [switch]$SkipRegressions)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/video-audio'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb {
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
        if ($LASTEXITCODE -ne 0) { throw 'Debug checks failed.' }
    }
    # QA only: update the existing debug target; a different signing key must be handled explicitly.
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    CheckDevice 'com.example.contentstudio.VideoAudioDeviceTest,com.example.contentstudio.VideoAudioUiDeviceTest' 4 'device'
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-audio (Join-Path $reportRoot 'files')
    & python tools/testing/verify_video_audio_files.py (Join-Path $reportRoot 'files')
    if ($LASTEXITCODE -ne 0) { throw 'Independent audio decode failed.' }
    if (-not $SkipLayouts) {
        foreach ($mode in @('tablet', 'narrow-large-text', 'dark')) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            CheckDevice 'com.example.contentstudio.VideoAudioUiDeviceTest' 1 $mode
            foreach ($name in @('controls','result')) {
                Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/video-audio-ui-$name.png" (Join-Path $reportRoot "$mode-$name.png")
            }
        }
    }
    if (-not $SkipRegressions) {
        Adb shell wm size reset
        Adb shell wm density reset
        Adb shell settings put system font_scale 1.0
        Adb shell cmd uimode night no
        CheckDevice 'com.example.contentstudio.VideoImageDeviceTest,com.example.contentstudio.VideoImageUiDeviceTest,com.example.contentstudio.VideoTextDeviceTest#allFormatsShowTextOnlyInGlobalOutputRangeAcrossJoinedSpeedAdjustedClips,com.example.contentstudio.VideoEditDeviceTest#joinedWorkRestoresCompletedOutputAndAtomicRetirementSurvivesWriteFailure' 6 'regression'
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-text (Join-Path $reportRoot 'text-regression-files')
        & python tools/testing/verify_video_text_files.py (Join-Path $reportRoot 'text-regression-files')
        if ($LASTEXITCODE -ne 0) { throw 'Independent text regression decode failed.' }
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-image (Join-Path $reportRoot 'image-regression-files')
        & python tools/testing/verify_video_image_files.py (Join-Path $reportRoot 'image-regression-files')
        if ($LASTEXITCODE -ne 0) { throw 'Independent image regression decode failed.' }
    }
    Write-Output 'Video audio QA passed; no release build or version bump.'
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Pop-Location
}
