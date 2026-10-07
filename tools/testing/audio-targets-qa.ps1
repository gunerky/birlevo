param([string]$Serial = 'emulator-5554', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/audio-expansion'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$avd = Adb emu avd name
if (($avd | Out-String) -notmatch '(?m)^ofice_qa\s*$') { throw 'Only the ofice_qa emulator is allowed.' }
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain 2>&1 |
            Out-File (Join-Path $reportRoot 'build-final.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug checks failed; inspect build-final.log.' }
    }
    foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $qaAdb -s $Serial install -r $apk
        if ($LASTEXITCODE -ne 0) { throw "Install failed: $apk. A signed release must be explicitly replaced on QA first." }
    }
    $log = Join-Path $reportRoot 'device-tests.log'
    Adb shell am instrument -w -r -e class 'com.example.contentstudio.AudioToolDeviceTest,com.example.contentstudio.AudioTargetsDeviceTest' `
        studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $log -Encoding utf8
    if ((Get-Content $log -Raw) -notmatch 'OK \(14 tests\)') { throw 'Audio checks failed; inspect device-tests.log.' }
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/audio-targets (Join-Path $reportRoot 'files')
    foreach ($name in @('audio-options-OGG', 'audio-result-OGG', 'audio-options-FLAC', 'audio-result-FLAC')) {
        Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$name.png" (Join-Path $reportRoot "$name.png")
    }
    Write-Output '14 audio checks passed. Inspect screenshots and actual files. No release built.'
} finally { Pop-Location }
