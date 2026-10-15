param([string]$Serial='emulator-5554',[switch]$SkipBuild,[switch]$ResourcesOnly)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaFolder=Join-Path $qaRoot 'output/development/media-budgets'
New-Item -ItemType Directory -Force -Path $qaFolder | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
if(((Adb emu avd name)|Out-String) -notmatch '(?m)^ofice_qa\s*$'){throw 'Dedicated QA emulator required'}
Push-Location $qaRoot
try {
    if(-not $SkipBuild){
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain *> (Join-Path $qaFolder 'build.log')
        if($LASTEXITCODE -ne 0){throw 'Debug build failed'}
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    $qaClasses=@('MediaResourceBudgetDeviceTest')
    if(-not $ResourcesOnly){$qaClasses+=@('ImageCompressionDeviceTest','ImageFormatsDeviceTest','PdfReaderDeviceTest','PhotoPdfDeviceTest','VideoOutputIntegrationDeviceTest')}
    foreach($qaClass in $qaClasses){
        $qaLog=Join-Path $qaFolder "$qaClass.log"
        Adb shell am instrument -w -r -e class "com.example.contentstudio.$qaClass" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner | Out-File -LiteralPath $qaLog -Encoding utf8
        $qaResult=Get-Content -LiteralPath $qaLog -Raw
        if($qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
            Get-Content -LiteralPath $qaLog -Tail 65;throw "QA failed: $qaClass"
        }
        "$qaClass : passed"
    }
    Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa/media-budgets/ $qaFolder
    $qaPython='C:/Users/user/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    $qaFfmpeg=(& python -c 'import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())' | Out-String).Trim()
    if($LASTEXITCODE -ne 0){throw 'Host FFmpeg unavailable'}
    & $qaPython tools/testing/verify_media_resources.py (Join-Path $qaFolder 'media-budgets') --ffmpeg $qaFfmpeg
    if($LASTEXITCODE -ne 0){throw 'Independent output decode failed'}
} finally { Pop-Location }
