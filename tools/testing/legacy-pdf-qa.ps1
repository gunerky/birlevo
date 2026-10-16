param(
    [string]$Serial='emulator-5554',
    [ValidateSet(24,28,36)][int]$Api=28,
    [ValidateRange(1,100)][int]$ProfilePages=24,
    [switch]$HighResolution,
    [switch]$SkipBuild
)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaSdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$qaAdb=Join-Path $qaSdk 'platform-tools/adb.exe'
$qaProfile=if($HighResolution){'4096'}else{'2048'}
$qaFolder=Join-Path $qaRoot "output/development/legacy-pdf/api$Api-$ProfilePages-$qaProfile"
New-Item -ItemType Directory -Force -Path $qaFolder | Out-Null
function Adb { & $qaAdb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB failed: $args"} }
$qaAvd=(Adb emu avd name | Out-String).Trim()
$qaExpected=if($Api -eq 36){'ofice_qa'}else{"ofice_qa_api$Api"}
if($qaAvd -notmatch "(?m)^$qaExpected\s*$"){throw "Dedicated $qaExpected emulator required"}
if((Adb shell getprop ro.build.version.sdk | Out-String).Trim() -ne "$Api"){throw 'Unexpected Android API'}
Push-Location $qaRoot
$qaInstalled=$false
try {
    if(-not $SkipBuild){
        & ./gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain *> (Join-Path $qaFolder 'build.log')
        if($LASTEXITCODE -ne 0){throw 'Debug build failed'}
    }
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    $qaInstalled=$true
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaFolder 'environment.txt') -Encoding utf8
    Adb shell getprop | Out-File (Join-Path $qaFolder 'environment.txt') -Append -Encoding utf8
    Adb shell dumpsys SurfaceFlinger | Select-String 'GLES:' | Out-File (Join-Path $qaFolder 'environment.txt') -Append -Encoding utf8
    Set-Content (Join-Path $qaFolder 'worker-memory.log') -Value '' -Encoding utf8
    foreach($qaClass in @('LegacyPdfMemoryDeviceTest','MediaResourceBudgetDeviceTest')){
        $qaLog=Join-Path $qaFolder "$qaClass.log"
        $qaProcess=Start-Process -FilePath $qaAdb -WindowStyle Hidden -PassThru -ArgumentList @(
            '-s',$Serial,'shell','am','instrument','-w','-r','-e','class',"com.example.contentstudio.$qaClass",
            '-e','profilePages',"$ProfilePages",'-e','highResolution',"$($HighResolution.IsPresent.ToString().ToLowerInvariant())",
            'studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner'
        ) -RedirectStandardOutput $qaLog -RedirectStandardError (Join-Path $qaFolder "$qaClass.stderr.log")
        while(-not $qaProcess.HasExited){
            # Worker PSS is sampled independently of the application/test process.
            "UTC=$([DateTime]::UtcNow.ToString('o'))" | Out-File (Join-Path $qaFolder 'worker-memory.log') -Append -Encoding utf8
            Adb shell dumpsys meminfo studio.birlevo.android:pdf_pages | Out-File (Join-Path $qaFolder 'worker-memory.log') -Append -Encoding utf8
            Start-Sleep -Milliseconds 500
        }
        $qaProcess.WaitForExit()
        $qaResult=Get-Content -LiteralPath $qaLog -Raw
        if($qaProcess.ExitCode -ne 0 -or $qaResult -notmatch 'OK \((\d+) tests?\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED'){
            Get-Content -LiteralPath $qaLog -Tail 60;throw "QA failed: $qaClass"
        }
        "$qaClass : passed"
    }
} finally {
    if($qaInstalled){
        # Keep successful evidence even if an unrelated regression class fails.
        & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/legacy-pdf/ $qaFolder
        & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/media-budgets/ $qaFolder
    }
    Pop-Location
}
