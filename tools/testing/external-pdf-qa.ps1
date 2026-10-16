param(
    [string]$Serial = 'emulator-5560',
    [ValidateSet(24,28,36)][int]$Api = 28
)
$ErrorActionPreference = 'Stop'
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$qaDir = Join-Path $qaRoot "output/development/external-pdf/api$Api-accepted"
$qaFixtures = Join-Path $qaRoot 'output/development/external-pdf/fixtures'
New-Item -ItemType Directory -Force $qaDir | Out-Null
function Adb { & $qaAdb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" } }
$qaExpected = if ($Api -eq 36) { 'ofice_qa' } else { "ofice_qa_api$Api" }
if (((Adb emu avd name) | Out-String) -notmatch "(?m)^$qaExpected\s*$") { throw 'Dedicated QA emulator required' }
if (((Adb shell getprop ro.build.version.sdk) | Out-String).Trim() -ne "$Api") { throw 'Unexpected API' }
foreach ($qaName in @('heavy.pdf','second.pdf','manifest.json')) {
    if (-not (Test-Path -LiteralPath (Join-Path $qaFixtures $qaName))) { throw "Generate fixtures first: $qaName" }
}
Push-Location $qaRoot
try {
    Adb install -r app/build/outputs/apk/debug/app-debug.apk
    Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    Get-FileHash app/build/outputs/apk/debug/app-debug.apk | Format-List | Out-File (Join-Path $qaDir 'environment.txt')
    Adb shell getprop | Out-File (Join-Path $qaDir 'environment.txt') -Append
    Adb shell mkdir -p /sdcard/Android/data/studio.birlevo.android/files/qa/external-pdf-input
    Adb push (Join-Path $qaFixtures 'heavy.pdf') (Join-Path $qaFixtures 'second.pdf') /sdcard/Android/data/studio.birlevo.android/files/qa/external-pdf-input/
    Adb shell input keyevent 224
    Adb shell wm dismiss-keyguard
    $qaRequest = 'no_backup/pdf-pages/worker-stop-request.txt'
    Adb shell run-as studio.birlevo.android rm -f $qaRequest
    $qaStopped = [System.Collections.Generic.HashSet[string]]::new()
    $qaNames = @{ pdf_pages = 'studio.birlevo.android:pdf_pages'; pdf_renderer = 'studio.birlevo.android:pdf_renderer' }
    # The API 36 image appends the component class to isolated process names.
    if ($Api -eq 36) {
        $qaNames.pdf_pages += ':com.example.contentstudio.media.PdfPageService'
        $qaNames.pdf_renderer += ':com.example.contentstudio.media.PdfRenderService'
    }
    $qaLog = Join-Path $qaDir 'tests.log'
    $qaProcess = Start-Process -FilePath $qaAdb -WindowStyle Hidden -PassThru -ArgumentList @(
        '-s',$Serial,'shell','am','instrument','-w','-r','-e','profilePhase','final','-e','class',
        'com.example.contentstudio.ExternalPdfResourceDeviceTest',
        'studio.birlevo.android.test/androidx.test.runner.AndroidJUnitRunner'
    ) -RedirectStandardOutput $qaLog -RedirectStandardError (Join-Path $qaDir 'tests.stderr.log')
    foreach ($qaKind in @('pdf_pages','pdf_renderer')) { Set-Content (Join-Path $qaDir "$qaKind-memory.log") '' }
    while (-not $qaProcess.HasExited) {
        # Fault injection belongs to the verified QA host, not the app UID or UiAutomation shell.
        $qaReady = ((& $qaAdb -s $Serial shell "run-as studio.birlevo.android cat $qaRequest 2>/dev/null") | Out-String).Trim()
        if ($qaReady -eq 'ready') {
            $qaMemory = (Adb shell dumpsys meminfo $qaNames.pdf_pages) | Out-String
            $qaPattern = '\*\* MEMINFO in pid ([0-9]+) \[' + [regex]::Escape($qaNames.pdf_pages) + '\] \*\*'
            foreach ($qaMatch in [regex]::Matches($qaMemory, $qaPattern)) {
                $qaTargetPid = $qaMatch.Groups[1].Value
                if ($qaStopped.Add($qaTargetPid)) {
                    if ($Api -eq 24) { Adb shell su 0 kill -9 $qaTargetPid }
                    else { Adb shell am crash $qaTargetPid }
                    "Stopped verified isolated PDF worker PID $qaTargetPid" | Out-File (Join-Path $qaDir 'worker-stop.log') -Append
                }
            }
        }
        foreach ($qaKind in @('pdf_pages','pdf_renderer')) {
            "UTC=$([DateTime]::UtcNow.ToString('o'))" | Out-File (Join-Path $qaDir "$qaKind-memory.log") -Append
            Adb shell dumpsys meminfo $qaNames[$qaKind] | Out-File (Join-Path $qaDir "$qaKind-memory.log") -Append
        }
        Start-Sleep -Milliseconds 250
    }
    $qaProcess.WaitForExit()
    $qaResult = Get-Content $qaLog -Raw
    if ($qaProcess.ExitCode -ne 0 -or $qaResult -notmatch 'OK \(8 tests\)' -or $qaResult -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        Get-Content $qaLog -Tail 60; throw 'External PDF QA failed'
    }
    "API $Api : eight external PDF resource tests passed"
} finally {
    & $qaAdb -s $Serial pull /sdcard/Android/data/studio.birlevo.android/files/qa/external-pdf/ $qaDir
    Pop-Location
}
