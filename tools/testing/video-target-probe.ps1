param([string]$Serial = 'emulator-5554', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$qaAdb = Join-Path $qaSdk 'platform-tools/adb.exe'
$reportRoot = Join-Path $projectRoot 'output/development/video-targets'
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null

# Never reset app data or install on an unverified device.
$avd = & $qaAdb -s $Serial emu avd name 2>&1
if ($LASTEXITCODE -ne 0 -or (($avd | Out-String) -notmatch '(?m)^ofice_qa\s*$')) {
    throw 'Only the ofice_qa emulator is allowed.'
}
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain 2>&1 |
            Out-File (Join-Path $reportRoot 'build-probe.log') -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'Debug verification failed; inspect build-probe.log.' }
    }
    foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk',
        'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $qaAdb -s $Serial install -r $apk
        if ($LASTEXITCODE -ne 0) { throw "Install failed: $apk" }
    }
    $log = Join-Path $reportRoot 'device-tests-final.log'
    & $qaAdb -s $Serial shell am instrument -w -r -e class com.example.contentstudio.VideoTargetProbeDeviceTest `
        studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-File $log -Encoding utf8
    if ($LASTEXITCODE -ne 0 -or (Get-Content $log -Raw) -notmatch 'OK \(5 tests\)') {
        throw 'Probe did not pass; inspect device-tests-final.log. Missing encoders are not conversion success.'
    }
    & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-targets `
        (Join-Path $reportRoot 'final-report')
    if ($LASTEXITCODE -ne 0) { throw 'Could not retrieve device reports.' }
    # Old diagnostics can coexist; accept only the six current target / option reports.
    foreach ($target in @('hevc', 'webm')) {
        foreach ($option in @('balanced-640', 'small-640', 'high-1280')) {
            $record = Get-Content (Join-Path $reportRoot "final-report/$target-$option.json") -Raw | ConvertFrom-Json
            if ($record.status -ne 'passed' -or $record.productionSupport -ne $false) {
                throw "Target trial unavailable or failed: $target-$option"
            }
        }
    }
    Write-Output "Technical trials passed. Reports: $reportRoot. No release version or package was created."
} finally { Pop-Location }
