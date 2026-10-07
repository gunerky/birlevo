param([string]$Serial = 'emulator-5554', [switch]$SkipBuild,
    [ValidateSet('tablet', 'narrow-large-text', 'dark')][string[]]$Modes = @('tablet', 'narrow-large-text', 'dark'))
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/audio-expansion/layouts'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$avd = Adb emu avd name
if (($avd | Out-String) -notmatch '(?m)^ofice_qa\s*$') { throw 'Only the ofice_qa emulator is allowed.' }
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat assembleDebugAndroidTest --console=plain 2>&1 | Out-File (Join-Path $reportRoot 'harness-build.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug test harness build failed.' }
        Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    }
    foreach ($mode in $Modes) {
        if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
        elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
        else { Adb shell wm size reset; Adb shell wm density reset }
        Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
        Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
        $log = Join-Path $reportRoot "$mode.log"
        Adb shell am instrument -w -r -e class 'com.example.contentstudio.AudioTargetsDeviceTest#oggUiPlaybackSaveSettingsInvalidateConsentAndRestoreDurableResult,com.example.contentstudio.AudioTargetsDeviceTest#flacUiPlaybackSaveSettingsInvalidateConsentAndRestoreDurableResult' `
            studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $log -Encoding utf8
        if ((Get-Content $log -Raw) -notmatch 'OK \(2 tests\)') { throw "Layout verification failed: $mode" }
        foreach ($image in @('audio-options-OGG', 'audio-result-OGG', 'audio-options-FLAC', 'audio-result-FLAC')) {
            Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$image.png" (Join-Path $reportRoot "$mode-$image.png")
        }
    }
    Write-Output "$($Modes.Count * 2) layout repeats passed; inspect screenshots. No release built."
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Pop-Location
}
