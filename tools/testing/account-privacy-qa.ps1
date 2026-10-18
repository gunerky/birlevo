param([Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidateSet(24,36)][int]$Api,
    [Parameter(Mandatory)][ValidateSet('eea','us','other')][string]$Region,
    [switch]$Network, [switch]$Offline, [switch]$OfflineOnly,
    [switch]$HomePreview)
$ErrorActionPreference = 'Stop'
if (-not $Network) { throw 'Explicit -Network is required to test the account messages.' }
if ($Offline -and $Region -ne 'eea') { throw 'Offline case is qualified with the EEA build.' }
if ($OfflineOnly -and -not $Offline) { throw '-OfflineOnly requires -Offline' }
if ($HomePreview -and $Region -ne 'eea') { throw 'Home preview uses the EEA adult QA scenario.' }
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaReview = if ($HomePreview) { 'home-ad-flow' } else { 'account-privacy' }
$qaOutput = Join-Path $qaRoot "output/development/$qaReview/api$Api-$Region"
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { 'ofice_qa_api24' }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$") { throw 'Dedicated QA emulator required' }
if (((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Unexpected API' }
function SetLegacyWifi([bool]$Enabled) {
    # The old image's svc wifi process exits 137. Use its real Settings switch instead.
    Adb shell am start -a android.settings.WIFI_SETTINGS | Out-Null
    Adb shell uiautomator dump /sdcard/qa-birlevo-wifi.xml | Out-Null
    Adb pull /sdcard/qa-birlevo-wifi.xml (Join-Path $qaOutput 'wifi.xml') | Out-Null
    [xml]$qaXml = Get-Content (Join-Path $qaOutput 'wifi.xml') -Raw
    $qaSwitch = $qaXml.SelectSingleNode("//node[@resource-id='com.android.settings:id/switch_widget']")
    if ($null -eq $qaSwitch -or $qaSwitch.enabled -ne 'true') { throw 'Expected Settings Wi-Fi switch' }
    if (($qaSwitch.checked -eq 'true') -ne $Enabled) {
        if ($qaSwitch.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Invalid Wi-Fi bounds' }
        $qaX = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $qaY = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        Adb shell input tap $qaX $qaY
        Start-Sleep -Seconds 2
        Adb shell uiautomator dump /sdcard/qa-birlevo-wifi.xml | Out-Null
        Adb pull /sdcard/qa-birlevo-wifi.xml (Join-Path $qaOutput 'wifi.xml') | Out-Null
        [xml]$qaXml = Get-Content (Join-Path $qaOutput 'wifi.xml') -Raw
        $qaSwitch = $qaXml.SelectSingleNode("//node[@resource-id='com.android.settings:id/switch_widget']")
        if (($qaSwitch.checked -eq 'true') -ne $Enabled) { throw 'Wi-Fi switch did not change' }
    }
    Adb shell input keyevent 4
}
function TestCase([string]$Name) {
    $qaLog = Join-Path $qaOutput "$Name.log"
    Adb shell am instrument -w -r -e ad-account-network true -e ad-account-region $Region -e class "com.example.contentstudio.AdAccountPrivacyDeviceTest#$Name" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
    $qaText = Get-Content -LiteralPath $qaLog -Raw
    if ($qaText -notmatch 'OK \(1 test\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[34]') {
        Get-Content -LiteralPath $qaLog -Tail 65
        throw "Account privacy QA failed or skipped: $Name"
    }
    "$Name : passed"
}
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    Adb shell settings put system font_scale 1.0
    if ($Api -ge 29) { Adb shell cmd uimode night no }
    if (-not $OfflineOnly) { switch ($Region) {
        'eea' {
            if ($HomePreview) {
                TestCase 'homePreviewRemovesBannerBeforePrivacyChangesAndRespectsReturnCooldown'
                TestCase 'homePreviewBackgroundAndRecreationDoNotRestartRequests'
                TestCase 'homePreviewUnknownAgeAndDecliningAgeNeverCreateConsentSession'
                TestCase 'homePreviewUnder13KeepsToolsWithoutConsentOrAdRequests'
                TestCase 'homePreviewYoungTeenUsesProtectedRequestsWithoutAdultConsent'
                TestCase 'homePreviewOlderTeenCanDisableAdsByWithholdingAge'
                TestCase 'homePreviewAdultToTeenCannotReuseAnAdultPrivacyForm'
            } else {
                TestCase 'eeaRejectAndReopenChoicesThenAccept'
                TestCase 'eeaAcceptThenWithdrawAndSampleBanner'
            }
        }
        'us' { TestCase 'regulatedUsChoicesCanBeReopened' }
        'other' { TestCase 'otherRegionDoesNotForceEuropeanForm' }
    } }
    if ($Offline) {
        $qaWifi = ((Adb shell settings get global wifi_on) | Out-String).Trim()
        $qaData = ((Adb shell settings get global mobile_data) | Out-String).Trim()
        try {
            if ($Api -ge 29) { Adb shell svc wifi disable } else { SetLegacyWifi $false }
            Adb shell svc data disable
            Start-Sleep -Seconds 2
            TestCase $(if ($HomePreview) { 'homePreviewOfflineKeepsPrivacyAndFileToolsAvailable' }
                else { 'unavailableProviderLeavesToolsUsable' })
        } finally {
            # Always attempt both restores. Original test details remain in the case log.
            try {
                if ($qaWifi -eq '1') {
                    if ($Api -ge 29) { Adb shell svc wifi enable } else { SetLegacyWifi $true }
                }
            } finally {
                if ($qaData -eq '1') { Adb shell svc data enable }
            }
        }
    }
} finally {
    try { Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/account-privacy $qaOutput }
    finally { Pop-Location }
}
