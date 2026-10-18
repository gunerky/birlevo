param([Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidateSet(24,36)][int]$Api,
    [Parameter(Mandatory)][ValidateSet('age','layouts','accessibility')][string]$Stage,
    [Parameter(Mandatory)][string]$AppArtifact,
    [Parameter(Mandatory)][string]$TestArtifact,
    [string]$OutputDirectory,
    [switch]$Network)
$ErrorActionPreference = 'Stop'
if ($Stage -eq 'age' -and -not $Network) { throw 'Age/account QA requires explicit -Network opt-in.' }
if ($Stage -ne 'age' -and $Api -ne 36) { throw 'Layout/accessibility matrix requires the dedicated API 36 emulator.' }
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaOutput = if ($OutputDirectory) { [IO.Path]::GetFullPath($OutputDirectory) }
    else { Join-Path $qaRoot "output/development/release-readiness/$Stage-api$Api" }
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { 'ofice_qa_api24' }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$" -or
    ((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Dedicated matching QA emulator required' }
function Case([string]$Test, [string]$Name, [string]$Locale = 'en-US') {
    $qaLog = Join-Path $qaOutput "$Name.log"
    Adb shell am instrument -w -r -e ad-account-network $(if ($Network) { 'true' } else { 'false' }) -e ad-account-region eea -e audit-layout $Name -e audit-locale $Locale -e audit-talkback $(if ($Stage -eq 'accessibility') { 'true' } else { 'false' }) -e class "com.example.contentstudio.$Test" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
    $qaText = Get-Content -LiteralPath $qaLog -Raw
    if ($qaText -notmatch 'OK \(1 test\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[34]') {
        Get-Content -LiteralPath $qaLog -Tail 45
        throw "Final UI case failed or skipped: $Name"
    }
    "$Name : passed"
}
Push-Location $qaRoot
$qaPreviousServices = $null
try {
    Adb install -r $AppArtifact
    Adb install -r $TestArtifact
    Get-FileHash $AppArtifact,$TestArtifact | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    if ($Stage -eq 'age') {
        Case 'AdAccountPrivacyDeviceTest#homePreviewUnknownAgeAndDecliningAgeNeverCreateConsentSession' 'unknown'
        Case 'AdAccountPrivacyDeviceTest#homePreviewUnder13KeepsToolsWithoutConsentOrAdRequests' 'under-13'
    } elseif ($Stage -eq 'layouts') {
        $qaFont = ((Adb shell settings get system font_scale) | Out-String).Trim()
        $qaSize = ((Adb shell wm size) | Out-String)
        $qaDensity = ((Adb shell wm density) | Out-String)
        $qaNight = ((Adb shell cmd uimode night) | Out-String).Trim()
        if ($qaNight -notmatch '^Night mode: (no|yes|auto|custom)$') { throw 'Unknown previous night mode' }
        $qaNightMode = $Matches[1]
        foreach ($qaMode in @('phone','phone-tr','large-dark','rtl-large-dark','tablet','tablet-large')) {
            $qaLocale = if ($qaMode -eq 'phone-tr') { 'tr-TR' } elseif ($qaMode -match '^rtl') { 'ar' } else { 'en-US' }
            if ($qaMode -match '^tablet') { Adb shell wm size 1600x2560; Adb shell wm density 240 }
            else { Adb shell wm size 720x1440; Adb shell wm density 360 }
            Adb shell settings put system font_scale $(if ($qaMode -match 'large') { '2.0' } else { '1.0' })
            Adb shell cmd uimode night $(if ($qaMode -match 'dark') { 'yes' } else { 'no' })
            Case 'WorkspaceUiDeviceTest#adaptiveHomeAndWorkspaceKeepActionsVisibleAndRespectFontScale' $qaMode $qaLocale
            foreach ($qaShot in @('home','inspector','image')) {
                Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/workspace-$qaMode-$qaLocale-$qaShot.png" $qaOutput *> (Join-Path $qaOutput "$qaMode-$qaShot-pull.log")
            }
        }
    } else {
        $qaPreviousServices = ((Adb shell settings get secure enabled_accessibility_services) | Out-String).Trim()
        $qaPreviousAccessibility = ((Adb shell settings get secure accessibility_enabled) | Out-String).Trim()
        $qaTalkBack = 'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
        $qaServices = if ($qaPreviousServices -in @('null','')) { $qaTalkBack } else { ((@($qaPreviousServices -split ':') + @($qaTalkBack)) | Select-Object -Unique) -join ':' }
        Adb shell settings put secure enabled_accessibility_services $qaServices
        Adb shell settings put secure accessibility_enabled 1
        Case 'WorkspaceUiDeviceTest#talkBackFocusAndNativeAccessibilityActionsReachFileConversion' 'talkback'
    }
} finally {
    try {
        if ($Stage -eq 'layouts' -and $null -ne $qaFont) {
            if ($qaSize -match 'Override size: (\d+x\d+)') { Adb shell wm size $Matches[1] } else { Adb shell wm size reset }
            if ($qaDensity -match 'Override density: (\d+)') { Adb shell wm density $Matches[1] } else { Adb shell wm density reset }
            if ($qaFont -eq 'null') { Adb shell settings delete system font_scale } else { Adb shell settings put system font_scale $qaFont }
            Adb shell cmd uimode night $qaNightMode
        }
        if ($null -ne $qaPreviousServices) {
            if ($qaPreviousServices -eq 'null') { Adb shell settings delete secure enabled_accessibility_services } else { Adb shell settings put secure enabled_accessibility_services $qaPreviousServices }
            if ($qaPreviousAccessibility -eq 'null') { Adb shell settings delete secure accessibility_enabled } else { Adb shell settings put secure accessibility_enabled $qaPreviousAccessibility }
        }
    } finally { Pop-Location }
}
