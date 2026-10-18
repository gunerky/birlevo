param([Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidateSet(24,36)][int]$Api,
    [switch]$Network, [string[]]$Cases)
$ErrorActionPreference = 'Stop'
if (-not $Network) { throw 'Explicit -Network is required for real consent services and sample ads.' }
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaOutput = Join-Path $qaRoot "output/development/resident-ad-media/api$Api"
$qaVideo = Join-Path $qaRoot 'output/development/video-resources/fixtures'
$qaPdf = Join-Path $qaRoot 'output/development/external-pdf/fixtures'
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { 'ofice_qa_api24' }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$" -or
    ((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Dedicated matching QA emulator required' }
$qaMatrix = [ordered]@{
    'basic' = @{ Test = 'AdResidentBasicMediaDeviceTest#fullImageAudioRoundTripsAndCopyConsentPreserveSources'; Age = 'protected' }
    'pdf-merge' = @{ Test = 'ExternalPdfResourceDeviceTest#mergeAndExtractKeepOnlySelectedEncodedResources'; Age = 'adult' }
    'pdf-raster' = @{ Test = 'ExternalPdfResourceDeviceTest#largeImportAnd4096PageExportsPreserveSourcesAndPixels'; Age = 'protected' }
    'pdf-cancel' = @{ Test = 'ExternalPdfResourceDeviceTest#cancellationAndDiskPressureCleanOnlyTheirOwnScratchAndSupportRetry'; Age = 'protected' }
    'video-size' = @{ Test = 'VideoResourceDeviceTest#selectedHighResolutionAndFullStillKeepPixelsAndSourceBytes'; Age = 'adult' }
    'video-cancel' = @{ Test = 'VideoResourceDeviceTest#cancellationAndLiveDiskPressurePreserveSourcesAndPreviousResultThenRetry'; Age = 'protected' }
    'video-background' = @{ Test = 'VideoResourceDeviceTest#completeLongVideoSurvivesBackgroundAndReattachesResult'; Age = 'adult' }
    'presentation-pdf' = @{ Test = 'PresentationResourceDeviceTest#profileMultipagePresentationPdf'; Age = 'adult' }
    'presentation-video' = @{ Test = 'PresentationResourceDeviceTest#profilePresentationSlideshow'; Age = 'protected' }
}
if (-not $Cases) { $Cases = @($qaMatrix.Keys) }
foreach ($qaCase in $Cases) { if (-not $qaMatrix.Contains($qaCase)) { throw "Unknown case: $qaCase" } }
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk,app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaOutput 'environment.txt') -Append
    foreach ($qaFamily in @('video-resource-input','external-pdf-input')) {
        Adb shell mkdir -p "/sdcard/Android/data/studio.birlevo.android/files/qa/$qaFamily"
    }
    foreach ($qaName in @('high.mp4','legacy.mp4','long.mp4')) {
        Adb push (Join-Path $qaVideo $qaName) /sdcard/Android/data/studio.birlevo.android/files/qa/video-resource-input/ | Out-Null
    }
    foreach ($qaName in @('heavy.pdf','second.pdf')) {
        Adb push (Join-Path $qaPdf $qaName) /sdcard/Android/data/studio.birlevo.android/files/qa/external-pdf-input/ | Out-Null
    }
    Get-FileHash "$qaVideo/high.mp4","$qaVideo/legacy.mp4","$qaVideo/long.mp4","$qaPdf/heavy.pdf","$qaPdf/second.pdf" | Format-List | Out-File (Join-Path $qaOutput 'fixture-hashes.txt')
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    Adb shell settings put system font_scale 1.0
    if ($Api -ge 29) { Adb shell cmd uimode night no }
    foreach ($qaCase in $Cases) {
        $qaName = $qaCase
        $qaDirectory = Join-Path $qaOutput $qaName
        New-Item -ItemType Directory -Force $qaDirectory | Out-Null
        # Clear only fixed instrumentation-evidence directories on this dedicated emulator.
        # User media, app work/session folders and host evidence are never removed here.
        foreach ($qaFolder in @('resident-ad-media','external-pdf','video-resources','presentation-resources')) {
            Adb shell rm -rf "/sdcard/Android/data/studio.birlevo.android/files/qa/$qaFolder"
        }
        $qaLog = Join-Path $qaDirectory 'tests.log'
        try {
            Adb shell am instrument -w -r -e resident-ad-network true -e resident-ad-age $qaMatrix[$qaCase].Age -e ad-account-network true -e ad-account-region eea -e profilePages 24 -e class "com.example.contentstudio.$($qaMatrix[$qaCase].Test)" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
            $qaText = Get-Content -LiteralPath $qaLog -Raw
            if ($qaText -notmatch 'OK \(1 test\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[34]') {
                Get-Content -LiteralPath $qaLog -Tail 55
                throw "Resident SDK workload failed or skipped: $qaCase"
            }
            "$qaCase ($($qaMatrix[$qaCase].Age)) : passed"
        } finally {
            foreach ($qaFolder in @('resident-ad-media','external-pdf','video-resources','presentation-resources')) {
                # Only the selected workload's directories exist. Resident-process proof is mandatory.
                $qaDevicePath = "/sdcard/Android/data/studio.birlevo.android/files/qa/$qaFolder"
                & $qaAdb -s $Serial shell test -d $qaDevicePath
                if ($LASTEXITCODE -eq 0) {
                    Adb pull $qaDevicePath $qaDirectory *> (Join-Path $qaDirectory "$qaFolder-pull.log")
                } elseif ($qaFolder -eq 'resident-ad-media') { throw 'Missing resident-process evidence' }
            }
        }
    }
} finally { Pop-Location }
exit 0
