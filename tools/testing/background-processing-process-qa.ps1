$ErrorActionPreference='Stop'
$taskAdb='C:/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$taskOutput=Join-Path (Get-Location) 'output/development/background-processing'
function Adb { & $taskAdb -s emulator-5554 @args; if($LASTEXITCODE -ne 0){throw 'QA emulator command failed'} }
if((Adb emu avd name) -notcontains 'ofice_qa'){throw 'Expected dedicated ofice_qa emulator'}
foreach($taskPhase in 'prepareCompletedPdfAndHeldVideoJob','verifyInterruptedJobCompletedPdfAndExplicitVideoRetry') {
    $taskLog=Join-Path $taskOutput "$taskPhase.log"
    Adb shell am instrument -w -r -e class "com.example.contentstudio.BackgroundProcessingProcessTest#$taskPhase" studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner *> $taskLog
    if((Get-Content $taskLog -Raw) -notmatch 'OK \(1 test\)'){throw "Process phase failed: $taskPhase"}
    if($taskPhase -eq 'prepareCompletedPdfAndHeldVideoJob'){Adb shell am force-stop studio.birlevo.android}
}
foreach($taskName in 'background-prepared-pid.txt','background-verified-pid.txt') {
    Adb pull "/sdcard/Android/data/studio.birlevo.android/files/qa/$taskName" (Join-Path $taskOutput $taskName)
}
