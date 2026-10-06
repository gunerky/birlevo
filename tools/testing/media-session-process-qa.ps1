$ErrorActionPreference='Stop'
$taskAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$taskOutput=Join-Path (Get-Location) 'output/development/media-sessions'
function Adb { & $taskAdb -s emulator-5554 @args; if($LASTEXITCODE -ne 0){throw 'QA emulator command failed'} }
if((Adb emu avd name) -notcontains 'ofice_qa'){throw 'Expected dedicated ofice_qa emulator'}
Adb shell am instrument -w -r -e class 'com.example.contentstudio.MediaSessionProcessTest#prepareSourcesAndSettings' studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> (Join-Path $taskOutput 'process-prepare.log')
if((Get-Content (Join-Path $taskOutput 'process-prepare.log') -Raw) -notmatch 'OK \(1 test\)'){throw 'Process preparation failed'}
Adb shell am force-stop studio.birlevo.android
Adb shell am instrument -w -r -e class 'com.example.contentstudio.MediaSessionProcessTest#verifyAfterExternalProcessStopAndContinueBothTools' studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> (Join-Path $taskOutput 'process-verify.log')
if((Get-Content (Join-Path $taskOutput 'process-verify.log') -Raw) -notmatch 'OK \(1 test\)'){throw 'Fresh-process restoration failed'}
foreach($taskName in 'session-video-restored.png','session-photos-restored.png','session-prepared-pid.txt','session-verified-pid.txt'){
    Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$taskName" (Join-Path $taskOutput $taskName)
}
