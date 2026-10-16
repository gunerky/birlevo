param([string]$Serial='emulator-5554',[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaFolder=Join-Path $qaRoot 'output/development/vp9-codecs'
New-Item -ItemType Directory -Force -Path $qaFolder | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa\s*$'){throw 'Dedicated QA required'}
Push-Location $qaRoot
try {
    if(-not $SkipBuild){
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory --console=plain *> (Join-Path $qaFolder 'build-final.log')
        if($LASTEXITCODE -ne 0){throw 'Debug build failed'}
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    $qaClass='com.example.contentstudio.VideoTargetProbeDeviceTest#inventoryRecordsEncodingAndDecodingSeparately,com.example.contentstudio.VideoOutputIntegrationDeviceTest,com.example.contentstudio.VideoTextDeviceTest,com.example.contentstudio.VideoFrameDeviceTest'
    $qaLog=Join-Path $qaFolder 'device-final.log'
    Adb shell am instrument -w -r -e class $qaClass studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    $qaResult=Get-Content -LiteralPath $qaLog -Raw
    if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
        Get-Content -LiteralPath $qaLog -Tail 45;throw 'Codec QA failed'
    }
    "$($Matches[1]) emulator tests passed"
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/video-targets/inventory.json (Join-Path $qaFolder 'inventory.json')
    foreach($qaName in @('vp9-codecs','video-text','video-frame')) {
        $qaDestination=Join-Path $qaFolder $qaName
        New-Item -ItemType Directory -Force -Path $qaDestination | Out-Null
        # Repeated directory pulls would silently nest old evidence: copy each current named file.
        $qaNames=Adb shell ls "/sdcard/Android/data/studio.birlevo.android/files/qa/$qaName/"
        foreach($qaFile in $qaNames){
            $qaFile=$qaFile.Trim()
            if($qaFile -match '^[a-zA-Z0-9_.-]+$') {
                Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$qaName/$qaFile" (Join-Path $qaDestination $qaFile)
            }
        }
    }
} finally {Pop-Location}
