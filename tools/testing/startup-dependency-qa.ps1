param(
    [string]$Serial = 'emulator-5554',
    [ValidateSet(24,36)][int]$Api = 36,
    [ValidatePattern('^[a-z0-9.-]+$')][string]$Review = 'startup-dependencies'
)
$ErrorActionPreference = 'Stop'
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaDir = Join-Path $qaRoot "output/development/$Review/api$Api"
New-Item -ItemType Directory -Force $qaDir | Out-Null
function Adb {
    & $qaAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
}
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { 'ofice_qa_api24' }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$") { throw 'Dedicated QA emulator required' }
if (((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Unexpected Android API' }
function TestCase([string]$Class, [string]$Name, [string]$Night = 'no') {
    $qaLog = Join-Path $qaDir "$Name.log"
    Adb shell am instrument -w -r -e audit-night $Night -e class "com.example.contentstudio.$Class" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner |
        Out-File $qaLog -Encoding utf8
    $qaResult = Get-Content $qaLog -Raw
    if ($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        Get-Content $qaLog -Tail 45
        throw "Failed: $Name"
    }
    "$Name passed"
}
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaDir 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaDir 'environment.txt') -Append
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    foreach ($qaMode in @('no','yes')) {
        TestCase 'SystemBarsDeviceTest' "system-bars-night-$qaMode" $qaMode
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/system-bars.png (Join-Path $qaDir "system-bars-night-$qaMode.png")
    }
    TestCase 'StudioDeviceTest#everyLanguageAppliesAndRtlDirectionsMatch' 'all-22-locales-and-recreation'
    TestCase 'StudioDeviceTest#presentationSurvivesRecreationSaveAndRelaunch' 'saved-project-recreation'
} finally {
    Pop-Location
}
