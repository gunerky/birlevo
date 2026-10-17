param([Parameter(Mandatory)][string]$Serial, [ValidateSet(24,36)][int]$Api,
    [switch]$Network, [switch]$Workflows)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaOutput=Join-Path $qaRoot "output/development/advertising-foundation/api$Api"
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
$qaExpected=if($Api -eq 36){'ofice_qa'}else{'ofice_qa_api24'}
if(((Adb emu avd name)|Out-String) -notmatch "(?m)^$qaExpected\s*$"){throw 'Dedicated QA emulator required'}
if(((Adb shell getprop ro.build.version.sdk)|Out-String).Trim() -ne "$Api"){throw 'Unexpected API'}
if($Workflows -and $Api -ne 36){throw 'The selected existing UI fixtures require API 36'}
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaOutput 'environment.txt') -Append
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    $qaCases=@(
        'AdPrivacyDeviceTest#ordinaryLaunchAndRecreationDoNotInitializeAdsOrWorkManager',
        'AdPrivacyDeviceTest#privacyPanelDoesNotContactProviderUntilExplicitActionAndToolsStillOpen',
        'AdPrivacyDeviceTest#privacyPanelSurvivesRecreationWithoutStartingNetwork',
        'AdPrivacyDeviceTest#packagedManifestUsesSampleIdAndExpectedNetworkPermissions'
    )
    if($Network){$qaCases+='AdPrivacyDeviceTest#realSampleConsentFailureOrDeadlineLeavesFileToolsAvailable'}
    if($Workflows){$qaCases+=@('ImageCompressionDeviceTest','AudioToolDeviceTest','PhotoPdfDeviceTest',
        'StorageCleanupDeviceTest','MediaOutputsDeviceTest')}
    foreach($qaCase in $qaCases){
        $qaLog=Join-Path $qaOutput ($qaCase.Replace('#','-')+'.log')
        # Each invocation has a fresh app process; SDK static state cannot leak between cases.
        Adb shell am instrument -w -r -e ad-network $(if($Network){'true'}else{'false'}) -e class "com.example.contentstudio.$qaCase" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
        $qaText=Get-Content -LiteralPath $qaLog -Raw
        if($qaText -notmatch 'OK \([1-9][0-9]* tests?\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
            Get-Content -LiteralPath $qaLog -Tail 60; throw "QA failed: $qaCase"
        }
        "$qaCase : passed"
    }
    if($Network){Adb pull '/sdcard/Android/data/studio.birlevo.android/files/qa/ad-consent-result.txt' (Join-Path $qaOutput 'ad-consent-result.txt')}
} finally {Pop-Location}
