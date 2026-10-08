param([string]$Serial = 'emulator-5554', [switch]$SkipBuild, [switch]$SkipLayouts, [switch]$SkipRegressions)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/video-frame'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
function CheckDevice([string]$Classes, [int]$Count, [string]$Name) {
    Adb shell am instrument -w -r -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner |
        Out-File (Join-Path $reportRoot "$Name.log") -Encoding utf8
    if ((Get-Content (Join-Path $reportRoot "$Name.log") -Raw) -notmatch "OK \($Count tests?\)") { throw "Device check failed: $Name" }
}
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain 2>&1 | Out-File (Join-Path $reportRoot 'build.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug checks failed.' }
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    CheckDevice 'com.example.contentstudio.VideoFrameDeviceTest,com.example.contentstudio.VideoFrameUiDeviceTest' 3 'device'
    # A fresh destination avoids adb nesting the remote directory inside a previous run.
    $filesRoot = Join-Path $reportRoot ('files-' + [DateTime]::UtcNow.Ticks)
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-frame $filesRoot
    & python tools/testing/verify_video_frame_files.py $filesRoot
    if ($LASTEXITCODE -ne 0) { throw 'Independent frame verification failed.' }
    if (-not $SkipLayouts) {
        foreach ($mode in @('tablet','narrow-large-text','dark')) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            CheckDevice 'com.example.contentstudio.VideoFrameUiDeviceTest' 1 $mode
            Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-frame-preview.png (Join-Path $reportRoot "$mode-preview.png")
            Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-frame-image-tool.png (Join-Path $reportRoot "$mode-image-tool.png")
        }
    }
    if (-not $SkipRegressions) {
        Adb shell wm size reset; Adb shell wm density reset; Adb shell settings put system font_scale 1.0; Adb shell cmd uimode night no
        CheckDevice 'com.example.contentstudio.ImageFormatsDeviceTest,com.example.contentstudio.VideoAudioUiDeviceTest' 4 'regression'
    }
    Write-Output 'Video frame QA passed; no release or version bump.'
} finally {
    Adb shell wm size reset; Adb shell wm density reset; Adb shell settings put system font_scale 1.0; Adb shell cmd uimode night no
    Pop-Location
}
