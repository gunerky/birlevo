param([string]$Serial='emulator-5554',[switch]$SkipBuild,[switch]$ArchiveOnly)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaFolder=Join-Path $qaRoot 'output/development/archive'
New-Item -ItemType Directory -Force -Path $qaFolder | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa\s*$'){throw 'Dedicated QA required'}
function Test-Device([string]$Class,[string]$Name,[string[]]$Extra=@()) {
    $qaLog=Join-Path $qaFolder "$Name.log"
    Adb shell am instrument -w -r -e class $Class @Extra studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
    $qaResult=Get-Content -LiteralPath $qaLog -Raw
    if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
        Get-Content -LiteralPath $qaLog -Tail 55;throw "QA failed: $Name"
    }
    "$Name : passed"
}
$qaSize=(Adb shell wm size)|Out-String;$qaDensity=(Adb shell wm density)|Out-String
$qaFont=((Adb shell settings get system font_scale)|Out-String).Trim();$qaNight=(Adb shell cmd uimode night)|Out-String
Push-Location $qaRoot
try {
    if(-not $SkipBuild){
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug exportRuntimeLicenseInventory --console=plain *> (Join-Path $qaFolder 'build.log')
        if($LASTEXITCODE -ne 0){throw 'Debug build failed'}
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Adb shell input keyevent 224;Adb shell wm dismiss-keyguard
    Test-Device 'com.example.contentstudio.ArchiveDeviceTest#exactBytesAtomicReplacementCorruptionSourceAndStaleGuards,com.example.contentstudio.ArchiveDeviceTest#cancellationNeverPublishesPartialInputOrExtraction,com.example.contentstudio.ArchiveDeviceTest#readsExistingBatchZipWithoutReencoding,com.example.contentstudio.ArchiveUiDeviceTest' 'device'
    if(-not $ArchiveOnly){
        Test-Device 'com.example.contentstudio.ArchiveDeviceTest#prepareProcessRestore' 'process-prepare'
        Adb shell am force-stop studio.birlevo.android
        Test-Device 'com.example.contentstudio.ArchiveDeviceTest#verifyProcessRestore' 'process-restored'
        Test-Device 'com.example.contentstudio.BatchImageUiDeviceTest,com.example.contentstudio.BatchMediaUiDeviceTest' 'batch-regression'
    }
    Adb shell settings put system font_scale 2.0
    Test-Device 'com.example.contentstudio.ArchiveUiDeviceTest#genericPickerRouteSaveRecreationAndClearKeepOriginal' 'phone-font200-ar' @('-e','audit-locale','ar','-e','audit-case','phone-font200-ar')
    Adb shell settings put system font_scale 1.0;Adb shell wm size 1280x800;Adb shell wm density 160;Adb shell cmd uimode night yes
    Adb shell am force-stop studio.birlevo.android
    Test-Device 'com.example.contentstudio.ArchiveUiDeviceTest#genericPickerRouteSaveRecreationAndClearKeepOriginal' 'tablet-dark' @('-e','audit-case','tablet-dark')
    $qaNames=Adb shell ls /sdcard/Android/data/studio.birlevo.android/files/qa/archive/
    foreach($qaFile in $qaNames){$qaFile=$qaFile.Trim();if($qaFile -match '^[a-zA-Z0-9_.-]+$'){
        Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/archive/$qaFile" (Join-Path $qaFolder $qaFile)
    }}
    & python tools/testing/verify_archive.py $qaFolder
    if($LASTEXITCODE -ne 0){throw 'Independent ZIP verification failed'}
} finally {
    if($qaSize -match 'Override size: (\d+x\d+)'){Adb shell wm size $Matches[1]}else{Adb shell wm size reset}
    if($qaDensity -match 'Override density: (\d+)'){Adb shell wm density $Matches[1]}else{Adb shell wm density reset}
    if($qaFont -match '^\d+(\.\d+)?$'){Adb shell settings put system font_scale $qaFont}else{Adb shell settings delete system font_scale}
    if($qaNight -match '(yes|no|auto|custom)'){Adb shell cmd uimode night $Matches[1]};Pop-Location
}
