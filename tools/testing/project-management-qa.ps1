param([string]$Serial = 'emulator-5554', [switch]$SkipInstall, [switch]$Layouts)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = 'C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaReports = Join-Path $projectRoot 'output/development/project-management'
New-Item -ItemType Directory -Force -Path $qaReports | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
Push-Location $projectRoot
try {
    if (-not $SkipInstall) {
        & $qaAdb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) {
            # Release/debug signing can differ. Only the dedicated QA app is removed.
            Adb uninstall studio.birlevo.android
            Adb install app/build/outputs/apk/debug/app-debug.apk
        }
        & $qaAdb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) {
            Adb uninstall studio.birlevo.android.test
            Adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        }
    }
    $classes = 'com.example.contentstudio.AtomicStorageDeviceTest,com.example.contentstudio.ProjectAutosaveDeviceTest,com.example.contentstudio.ProjectManagementDeviceTest,com.example.contentstudio.MediaSessionDeviceTest'
    $modes = if ($Layouts) { @('large-text','dark','tablet') } else { @('core') }
    foreach ($mode in $modes) {
        if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
        elseif ($mode -eq 'large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
        else { Adb shell wm size reset; Adb shell wm density reset }
        Adb shell settings put system font_scale $(if ($mode -eq 'large-text') { '2.0' } else { '1.0' })
        Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
        $selected = if ($Layouts) { 'com.example.contentstudio.ProjectManagementDeviceTest' } else { $classes }
        $log = Join-Path $qaReports "$mode.log"
        Adb shell am instrument -w -r -e class $selected studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $log -Encoding utf8
        $report = Get-Content -LiteralPath $log -Raw
        if ($report -notmatch 'OK \((\d+) tests?\)') { Get-Content -LiteralPath $log -Tail 35; throw "Failed: $mode" }
        Write-Output "$mode : $($Matches[1]) tests passed"
    }
} finally {
    Adb shell settings put system font_scale 1.0
    Adb shell cmd uimode night no
    Adb shell wm size reset
    Adb shell wm density reset
    Pop-Location
}
