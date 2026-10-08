param(
    [string]$BaselineApk = 'output/development/workspace/baseline/app/build/outputs/apk/debug/app-debug.apk',
    [string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
$qaAdb = Join-Path $qaSdk 'platform-tools/adb.exe'
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
Push-Location $projectRoot
try {
    $baseline = (Resolve-Path -LiteralPath $BaselineApk).Path
    $current = (Resolve-Path -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk').Path
    $tests = (Resolve-Path -LiteralPath 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').Path
    $reports = Join-Path $projectRoot 'output/development/workspace'
    Adb install -r $tests
    foreach ($variant in @('current','baseline')) {
        Adb install -r $(if ($variant -eq 'current') { $current } else { $baseline })
        $log = Join-Path $reports "comparison-$variant.log"
        Adb shell am instrument -w -r -e comparison-variant $variant -e class com.example.contentstudio.WorkspaceComparisonDeviceTest studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner |
            Out-File $log -Encoding utf8
        if ((Get-Content -LiteralPath $log -Raw) -notmatch 'OK \(2 tests\)') {
            Get-Content -LiteralPath $log -Tail 30
            throw "Comparison tasks failed: $variant"
        }
        Write-Output "$variant : 2 comparison tasks passed"
    }
} finally {
    if ($current) { Adb install -r $current }
    if ($reports) { Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa (Join-Path $reports ('comparison-' + [DateTime]::UtcNow.Ticks)) }
    Pop-Location
}
