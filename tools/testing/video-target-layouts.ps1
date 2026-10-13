param([string]$Serial = 'emulator-5554',
    [ValidateSet('tablet', 'narrow-large-text', 'dark')][string[]]$Modes = @('tablet', 'narrow-large-text', 'dark'))
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$qaAdb = Join-Path $qaSdk 'platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/video-integration/layouts'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$avd = Adb emu avd name
if (($avd | Out-String) -notmatch '(?m)^ofice_qa\s*$') { throw 'Only the ofice_qa emulator is allowed.' }
# Uses the installed debug application/test pair. Never clears application data.
try {
    foreach ($mode in $Modes) {
        if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
        elseif ($mode -eq 'narrow-large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
        else { Adb shell wm size reset; Adb shell wm density reset }
        Adb shell settings put system font_scale $(if ($mode -eq 'narrow-large-text') { '2.0' } else { '1.0' })
        Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
        $log = Join-Path $reportRoot "$mode.log"
        Adb shell am instrument -w -r -e class 'com.example.contentstudio.VideoOutputIntegrationDeviceTest#webmUiRestoresExportsAndHandsOwnedBytesToAudio' `
            studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $log -Encoding utf8
        if ((Get-Content $log -Raw) -notmatch 'OK \(1 test\)') { throw "Layout verification failed: $mode" }
        foreach ($image in @('video-options-WEBM_VP8', 'video-WEBM_VP8')) {
            Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$image.png" (Join-Path $reportRoot "$mode-$image.png")
        }
    }
    Write-Output "$($Modes.Count) layout repeats passed; inspect screenshots. No release built."
} finally {
    Adb shell wm size reset
    Adb shell wm density reset
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
}
