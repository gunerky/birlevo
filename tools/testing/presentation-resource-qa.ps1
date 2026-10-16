param(
    [string]$Serial = 'emulator-5554',
    [ValidateSet(24,28,36)][int]$Api = 28,
    [ValidateSet('baseline','final')][string]$Phase = 'final',
    [ValidateRange(1,100)][int]$ProfilePages = 24,
    [switch]$ProfileOnly
)
$ErrorActionPreference = 'Stop'
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaDir = Join-Path $qaRoot "output/development/presentation-resources/api$Api-$Phase-$ProfilePages"
New-Item -ItemType Directory -Force $qaDir | Out-Null
function Adb {
    & $qaAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
}
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { "ofice_qa_api$Api" }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$") { throw 'Dedicated QA emulator required' }
if (((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Unexpected Android API' }
$qaClass = 'com.example.contentstudio.PresentationResourceDeviceTest'
if ($ProfileOnly) { $qaClass = "$qaClass#profileMultipagePresentationPdf,$qaClass#profilePresentationSlideshow" }
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaDir 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaDir 'environment.txt') -Append
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    Adb shell am instrument -w -r -e profilePhase $Phase -e profilePages $ProfilePages -e class $qaClass studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner |
        Out-File (Join-Path $qaDir 'tests.log') -Encoding utf8
    $qaResult = Get-Content (Join-Path $qaDir 'tests.log') -Raw
    if ($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        Get-Content (Join-Path $qaDir 'tests.log') -Tail 65
        throw 'Presentation resource QA failed'
    }
    "API $Api : $($Matches[1]) tests passed"
} finally {
    & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/presentation-resources/ $qaDir
    Pop-Location
}
