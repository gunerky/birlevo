param([string]$Serial = 'emulator-5554', [switch]$SkipInstall, [switch]$TalkBack, [switch]$CoreOnly,
    [ValidateSet('phone','large-text','tablet','dark')][string[]]$Modes = @('phone','large-text','tablet','dark'))
$ErrorActionPreference = 'Stop'
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = 'C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaReports = Join-Path $qaRoot 'output/development/accessibility-rtl'
New-Item -ItemType Directory -Force -Path $qaReports | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
if (((Adb emu avd name) | Out-String) -notmatch '(?m)^ofice_qa(?:_api35)?\s*$') { throw 'Only dedicated QA emulators are allowed.' }
$qaPreviousServices = ((Adb shell settings get secure enabled_accessibility_services) | Out-String).Trim()
$qaPreviousEnabled = ((Adb shell settings get secure accessibility_enabled) | Out-String).Trim()
$qaPreviousFont = ((Adb shell settings get system font_scale) | Out-String).Trim()
function RunCase([string]$Mode, [string]$Locale, [string]$Classes) {
    $log = Join-Path $qaReports "$Mode-$Locale.log"
    Adb shell am instrument -w -r -e audit-layout $Mode -e audit-locale $Locale -e audit-talkback $(if ($TalkBack) { 'true' } else { 'false' }) -e class $Classes studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $log -Encoding utf8
    $report = Get-Content -LiteralPath $log -Raw
    if ($report -notmatch 'OK \((\d+) tests?\)' -or $report -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { Get-Content -LiteralPath $log -Tail 28; throw "Failed: $Mode / $Locale" }
    Write-Output "$Mode / $Locale : $($Matches[1]) tests passed"
}
Push-Location $qaRoot
try {
    if (-not $SkipInstall) {
        Adb install -r app/build/outputs/apk/debug/app-debug.apk
        Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    }
    if ($TalkBack) {
        $qaService = 'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
        $qaServices = if ($qaPreviousServices -in @('null','')) { $qaService } else { ((@($qaPreviousServices -split ':') + @($qaService)) | Select-Object -Unique) -join ':' }
        Adb shell settings put secure enabled_accessibility_services $qaServices
        Adb shell settings put secure accessibility_enabled 1
        $classes = 'com.example.contentstudio.AccessibilityRtlDeviceTest#mixedFilenameAndProjectMenuRemainReachableWithNativeAccessibilityActions,com.example.contentstudio.AccessibilityRtlDeviceTest#videoTimeAndImageQualityExposeActualValuesAndNativeAdjustment,com.example.contentstudio.AccessibilityRtlDeviceTest#slideSelectionPanelNameAndQualityReachAndroidAccessibilityNodes,com.example.contentstudio.WorkspaceUiDeviceTest#talkBackFocusAndNativeAccessibilityActionsReachFileConversion'
        foreach ($locale in @('en-US','ar','fa')) { RunCase 'talkback' $locale $classes }
    } else {
        $classes = 'com.example.contentstudio.AccessibilityRtlDeviceTest'
        $modes = if ($CoreOnly) { @('phone') } else { $Modes }
        foreach ($mode in $modes) {
            if ($mode -eq 'tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            elseif ($mode -eq 'large-text') { Adb shell wm size 720x1440; Adb shell wm density 360 }
            else { Adb shell wm size reset; Adb shell wm density reset }
            Adb shell settings put system font_scale $(if ($mode -eq 'large-text') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($mode -eq 'dark') { 'yes' } else { 'no' })
            foreach ($locale in @('ar','fa')) { RunCase $mode $locale $classes }
        }
    }
} finally {
    if ($qaPreviousServices -eq 'null') { Adb shell settings delete secure enabled_accessibility_services }
    else { Adb shell settings put secure enabled_accessibility_services $qaPreviousServices }
    if ($qaPreviousEnabled -eq 'null') { Adb shell settings delete secure accessibility_enabled }
    else { Adb shell settings put secure accessibility_enabled $qaPreviousEnabled }
    if ($qaPreviousFont -eq 'null') { Adb shell settings delete system font_scale }
    else { Adb shell settings put system font_scale $qaPreviousFont }
    Adb shell cmd uimode night no
    Adb shell wm size reset
    Adb shell wm density reset
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/accessibility-rtl (Join-Path $qaReports ('screens-' + [DateTime]::UtcNow.Ticks))
    Pop-Location
}
