param([string]$Serial = 'emulator-5554', [switch]$SkipBuild, [switch]$SkipLayouts, [switch]$SkipLongDuration)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/video-canvas-speed'
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
            Out-File (Join-Path $reportRoot 'verified-build.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug checks failed.' }
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    $engine = if ($SkipLongDuration) {
        'com.example.contentstudio.VideoTransformDeviceTest#slowPortraitFitAndFastSquareCropKeepOrderTimingAudioAndAllFormats,com.example.contentstudio.VideoTransformDeviceTest#speedAlsoChangesGeneratedSilenceWithoutShiftingSound'
    } else { 'com.example.contentstudio.VideoTransformDeviceTest' }
    CheckDevice "$engine,com.example.contentstudio.VideoEditDeviceTest#joinedWorkRestoresCompletedOutputAndAtomicRetirementSurvivesWriteFailure,com.example.contentstudio.VideoEditUiDeviceTest" $(if ($SkipLongDuration) { 4 } else { 5 }) 'device-final'
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-transforms (Join-Path $reportRoot 'final-files')
    & python tools/testing/verify_video_transform_files.py (Join-Path $reportRoot 'final-files')
    if ($LASTEXITCODE -ne 0) { throw 'Independent decoding failed.' }
    if (-not $SkipLayouts) {
        foreach ($mode in @('tablet', 'narrow-large-text', 'dark')) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            CheckDevice 'com.example.contentstudio.VideoEditUiDeviceTest' 1 $mode
            foreach ($name in @('sources', 'options', 'result', 'player')) {
                Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/video-edit-$name.png" (Join-Path $reportRoot "$mode-$name.png")
            }
        }
    }
    Write-Output 'Video canvas/speed QA passed. No release build or version bump.'
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Pop-Location
}
