param([Parameter(Mandatory)][string]$Serial, [ValidateSet(24,36)][int]$Api, [switch]$Workflows)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaOutput=Join-Path $qaRoot "output/development/storage-cleanup/api$Api"
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
    $qaCases=@('StorageCleanupDeviceTest')
    if($Workflows){$qaCases+=@(
        'MediaSessionDeviceTest', 'MediaOutputsDeviceTest', 'PhotoPdfDeviceTest', 'PdfReaderDeviceTest',
        'SlideshowDeviceTest#orphanCleanupPreservesSavedAssetsResultsAndFreshJobs',
        'SlideshowDeviceTest#cancelRemovesPartialPngsAndVideoButKeepsPrivateSources',
        'SlideshowDeviceTest#settingsAndFinishedResultRestoreAfterOriginalRemovalAndViewModelRecreation',
        'PresentationPptxDeviceTest#cancellationOverflowCorruptionAndSourceOverwriteNeverSaveBadOutput',
        'PresentationPptxDeviceTest#foregroundResultRestoresWithoutViewModelAndEditsInvalidateOnlyRelevantContent',
        'ProjectManagementDeviceTest#deletionPreservesSharedProjectImageOriginalAndExportedCopy',
        'TextDocumentDeviceTest#checkpointIsSmallAndRestoresSettingsWhileFailedReplacementKeepsSource',
        'BatchArchiveFlowDeviceTest#queueSelectionArchiveSelectionCancellationAndRetryNeverTouchSavedZip',
        'BackgroundProcessingDeviceTest',
        'BackgroundProcessingProcessTest#prepareCompletedPdfAndHeldVideoJob',
        'BackgroundProcessingProcessTest#verifyInterruptedJobCompletedPdfAndExplicitVideoRetry'
    )}
    foreach($qaCase in $qaCases) {
        $qaLog=Join-Path $qaOutput ($qaCase.Replace('#','-')+'.log')
        Adb shell am instrument -w -r -e class "com.example.contentstudio.$qaCase" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
        $qaText=Get-Content -LiteralPath $qaLog -Raw
        if($qaText -notmatch 'OK \([1-9][0-9]* tests?\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
            Get-Content -LiteralPath $qaLog -Tail 65; throw "QA failed: $qaCase"
        }
        "$qaCase : passed"
        if($qaCase -eq 'BackgroundProcessingProcessTest#prepareCompletedPdfAndHeldVideoJob'){
            Adb shell am force-stop studio.birlevo.android
        }
    }
    if($Workflows){foreach($qaName in @('background-prepared-pid.txt','background-verified-pid.txt')){
        Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$qaName" (Join-Path $qaOutput $qaName)
    }}
} finally { Pop-Location }
