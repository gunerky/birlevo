param([string]$Serial='emulator-5554',[ValidateSet(24,36)][int]$Api=36,[switch]$SkipBuild,[string]$StartAt='')
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaDir=Join-Path $qaRoot "output/development/media3-1.11.1/api$Api"
New-Item -ItemType Directory -Force $qaDir | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
$qaExpected=if($Api -eq 36){'ofice_qa'}else{'ofice_qa_api24'}
if(((Adb emu avd name)|Out-String) -notmatch "(?m)^$qaExpected\s*$"){throw 'Dedicated QA emulator required'}
if(((Adb shell getprop ro.build.version.sdk)|Out-String).Trim() -ne "$Api"){throw 'Unexpected Android API'}
Push-Location $qaRoot
try {
    if(-not $SkipBuild){
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain *> (Join-Path $qaDir 'build.log')
        if($LASTEXITCODE -ne 0){throw 'Debug checks failed'}
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaDir 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaDir 'environment.txt') -Append
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    $qaClasses=if($Api -eq 36){@('Media3MuxerDeviceTest','AudioTargetsDeviceTest','audio.AudioEditDeviceTest',
        'VideoOutputIntegrationDeviceTest','VideoEditDeviceTest','VideoTextDeviceTest','VideoImageDeviceTest',
        'VideoAudioDeviceTest','SlideshowDeviceTest','SlideshowUiDeviceTest')}
    else {@('Media3MuxerDeviceTest#webmPreservesEncodedPacketsTimestampsAndKeyframesAtNonzeroOffsets',
        'Media3MuxerDeviceTest#aacEncodingStillUsesRequestedBitrateAndProfile',
        'Media3MuxerDeviceTest#joinedSpeedAdjustedAvcStillEncodesItsSoundOnLegacyAndroid')}
    if($StartAt){
        $qaStart=[Array]::IndexOf($qaClasses,$StartAt)
        if($qaStart -lt 0){throw 'Unknown resume class'}
        $qaClasses=$qaClasses[$qaStart..($qaClasses.Length-1)]
    }
    foreach($qaClass in $qaClasses){
        $qaLog=Join-Path $qaDir (($qaClass -replace '[.#]','-')+'.log')
        Adb shell am instrument -w -r -e class "com.example.contentstudio.$qaClass" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $qaLog -Encoding utf8
        $qaResult=Get-Content $qaLog -Raw
        if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
            Get-Content $qaLog -Tail 50;throw "Failed: $qaClass"
        }
        "$qaClass passed"
    }
} finally {
    foreach($qaName in @('media3-migration','audio-targets','audio-edits','vp9-codecs','video-edits','video-text','video-image','video-audio','slideshow')){
        $qaRemote="/sdcard/Android/data/studio.birlevo.android/files/qa/$qaName"
        & $qaAdb -s $Serial shell test -d $qaRemote
        if($LASTEXITCODE -eq 0){ & $qaAdb -s $Serial pull "$qaRemote/" $qaDir }
    }
    Adb get-state | Out-Null
    Pop-Location
}
