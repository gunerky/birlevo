param([string]$Serial, [ValidateSet(24,28,36)][int]$Api, [switch]$ProfileOnly)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaOutput=Join-Path $qaRoot "output/development/video-resources/api$Api-accepted"
$qaFixtures=Join-Path $qaRoot 'output/development/video-resources/fixtures'
New-Item -ItemType Directory -Force $qaOutput | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
$qaExpected=if($Api -eq 36){'ofice_qa'}else{"ofice_qa_api$Api"}
if(((Adb emu avd name)|Out-String) -notmatch "(?m)^$qaExpected\s*$"){throw 'Dedicated QA emulator required'}
if(((Adb shell getprop ro.build.version.sdk)|Out-String).Trim() -ne "$Api"){throw 'Unexpected API'}
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaOutput 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaOutput 'environment.txt') -Append
    Adb shell mkdir -p /sdcard/Android/data/studio.birlevo.android/files/qa/video-resource-input
    foreach($qaName in @('high.mp4','rotated.mp4','medium.mp4','medium-rotated.mp4','legacy.mp4','legacy-rotated.mp4','long.mp4','manifest.json')) {
        Adb push (Join-Path $qaFixtures $qaName) /sdcard/Android/data/studio.birlevo.android/files/qa/video-resource-input/
    }
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    $qaCases=@('profileSourcePostersWithoutChangingDimensionsOrOriginals')
    if(-not $ProfileOnly){$qaCases+=@('highResolutionCodecCapabilityIsExplicitAndPreservesSource',
        'selectedHighResolutionAndFullStillKeepPixelsAndSourceBytes','previewMemoryAdmissionDoesNotLowerFullStillResolution',
        'cancellationAndLiveDiskPressurePreserveSourcesAndPreviousResultThenRetry',
        'completeLongVideoSurvivesBackgroundAndReattachesResult','prepareLongSessionForExternalStop',
        'verifyLongSessionAfterExternalStopAndExplicitRetry')}
    foreach($qaCase in $qaCases) {
        $qaLog=Join-Path $qaOutput "$qaCase.log"
        Adb shell am instrument -w -r -e class "com.example.contentstudio.VideoResourceDeviceTest#$qaCase" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $qaLog
        $qaText=Get-Content -LiteralPath $qaLog -Raw
        if($qaText -notmatch 'OK \(1 test\)' -or $qaText -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
            Get-Content -LiteralPath $qaLog -Tail 65; throw "QA failed: $qaCase"
        }
        "$qaCase : passed"
        if($qaCase -eq 'prepareLongSessionForExternalStop'){Adb shell am force-stop studio.birlevo.android}
    }
} finally {
    & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-resources $qaOutput
    Pop-Location
}
