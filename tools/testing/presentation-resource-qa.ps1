param([string]$Serial='emulator-5554',[ValidateSet(24,28,36)][int]$Api=28,
    [ValidateRange(1,100)][int]$Pages=100,[switch]$SkipBuild,[switch]$UiRegression)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$qaAdb=Join-Path $qaSdk 'platform-tools/adb.exe'
$qaFolder=Join-Path $qaRoot "output/development/presentation-resources/api$Api-$Pages"
New-Item -ItemType Directory -Force -Path $qaFolder | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
$qaExpected=if($Api -eq 36){'ofice_qa'}else{"ofice_qa_api$Api"}
if(((Adb emu avd name)|Out-String) -notmatch "(?m)^$qaExpected\s*$"){throw 'Dedicated QA emulator required'}
if(((Adb shell getprop ro.build.version.sdk)|Out-String).Trim() -ne "$Api"){throw 'Unexpected API'}
if($UiRegression -and $Api -ne 36){throw 'UI regression locale setup requires the API 36 QA profile'}
Push-Location $qaRoot
$qaInstalled=$false
try {
    if(-not $SkipBuild){
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain *> (Join-Path $qaFolder 'build.log')
        if($LASTEXITCODE -ne 0){throw 'Debug checks failed'}
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    $qaInstalled=$true
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaFolder 'environment.txt') -Encoding utf8
    Adb shell getprop | Out-File (Join-Path $qaFolder 'environment.txt') -Append -Encoding utf8
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    $qaClasses=@('PresentationResourceDeviceTest','LegacyPdfMemoryDeviceTest')
    if($UiRegression){$qaClasses+=@('PresentationPdfDeviceTest','PhotoPdfDeviceTest','SlideshowDeviceTest','SlideshowUiDeviceTest','PresentationPptxDeviceTest')}
    foreach($qaClass in $qaClasses){
        $qaLog=Join-Path $qaFolder "$qaClass.log"
        Adb shell am instrument -w -r -e class "com.example.contentstudio.$qaClass" -e pages "$Pages" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File $qaLog -Encoding utf8
        $qaResult=Get-Content $qaLog -Raw
        if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
            Get-Content $qaLog -Tail 60;throw "QA failed: $qaClass"
        }
        "$qaClass passed"
    }
} finally {
    if($qaInstalled){
        & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/presentation-resources/ $qaFolder
        & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/legacy-pdf/ $qaFolder
    }
    Pop-Location
}
