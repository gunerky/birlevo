param([Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidateSet(24,36)][int]$Api,
    [switch]$Network, [string[]]$Cases, [switch]$SkipInstall)
$ErrorActionPreference = 'Stop'
if (-not $Network) { throw 'Explicit -Network is required for account consent and sample ads.' }
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaOutput = Join-Path $qaRoot "output/development/release-readiness/api$Api"
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { 'ofice_qa_api24' }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$" -or
    ((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Dedicated matching QA emulator required' }
$qaMatrix = [ordered]@{
    'picker' = @('WorkspaceUiDeviceTest#generalPickerOffersOnlyOperationsForItsFileAndRestoresSelection','protected')
    'routes' = @('WorkspaceUiDeviceTest#firstPickedMediaActionWaitsForSessionAndOpensWithOneTap','protected')
    'inspector' = @('WorkspaceUiDeviceTest#imageOutputInspectorBackAndCopyGuardsKeepTheSourceAndInvalidateOldResult','protected')
    'image-save' = @('ImageCompressionDeviceTest#selectAdjustPreviewRecreateAndExportPreservesOriginal','protected')
    'save-failure' = @('ImageCompressionDeviceTest#originalDestinationIsRejectedAndSaveFailureCanBeRetried','protected')
    'late-save' = @('ImageCompressionDeviceTest#cancelledFailedAndObsoleteDestinationsCannotSaveAnotherResult','protected')
    'larger-copy' = @('OutputTransferDeviceTest#biggerOutputRequiresSeparateApprovalForOpenShareAndTransfer','protected')
    'new-project' = @('OutputTransferDeviceTest#compressedImageCreatesDurablePresentationWithoutASecondPicker','adult')
    'append-project' = @('OutputTransferDeviceTest#transferAppendsWithoutReplacingExistingSlidesOrTheme','protected')
    'share-open' = @('OutputTransferDeviceTest#openAndShareUseReadableSnapshotsAndRecoverAfterCacheCleanup','protected')
    'grant' = @('MediaOutputsDeviceTest#anotherApplicationCanReadTheExplicitlyGrantedSnapshot','protected')
    'photo-pdf' = @('PhotoPdfDeviceTest#biggerImageTransfersToPhotoPdfOnlyAfterApprovalAndSurvivesCacheCleanup','protected')
    'audio' = @('AudioEditUiDeviceTest#rangeOrderSaveAndDurableMultiSourceRestoreInvalidateOldResultConsent','protected')
    'video' = @('VideoEditUiDeviceTest#bothClipControlsPlaybackOrderRemovalAndResultStayUsable','protected')
    'presentation' = @('PresentationModeUiDeviceTest#slideImageCreatesSavesTransfersAndInvalidatesWithoutLosingDraft','protected')
    'presenter' = @('PresentationModeUiDeviceTest#notesSaveAndFullscreenTimerNavigationRestoreTheEditorAndWindow','protected')
}
if (-not $Cases) { $Cases = @($qaMatrix.Keys) }
foreach ($qaCase in $Cases) { if (-not $qaMatrix.Contains($qaCase)) { throw "Unknown case: $qaCase" } }
Push-Location $qaRoot
try {
    if (-not $SkipInstall) {
        Adb install -r app/build/outputs/apk/debug/app-debug.apk
        Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    }
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk,app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaOutput 'environment.txt') -Append
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    foreach ($qaCase in $Cases) {
        $qaDirectory = Join-Path $qaOutput $qaCase
        New-Item -ItemType Directory -Force $qaDirectory | Out-Null
        $qaLog = Join-Path $qaDirectory 'tests.log'
        Adb shell am instrument -w -r -e resident-ad-network true -e resident-ad-age $qaMatrix[$qaCase][1] -e ad-account-network true -e ad-account-region eea -e class "com.example.contentstudio.$($qaMatrix[$qaCase][0])" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/resident-ad-media/proof.json $qaDirectory *> (Join-Path $qaDirectory 'pull.log')
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/resident-ad-media/memory.csv $qaDirectory *> (Join-Path $qaDirectory 'memory-pull.log')
        $qaText = Get-Content -LiteralPath $qaLog -Raw
        $qaProof = Get-Content (Join-Path $qaDirectory 'proof.json') -Raw | ConvertFrom-Json
        if ($qaText -notmatch 'OK \(1 test\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[34]' -or
            -not $qaProof.passed -or $qaProof.case -ne $qaMatrix[$qaCase][0].Replace('#','-')) {
            Get-Content -LiteralPath $qaLog -Tail 45
            throw "Integrated UI case failed or skipped: $qaCase"
        }
        "$qaCase : passed ($($qaMatrix[$qaCase][1]))"
    }
} finally { Pop-Location }
