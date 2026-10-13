param([string]$Version = '0.1.9', [string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$taskOutput = Join-Path $taskRoot "output/development/$Version/foreground-demo"
function Adb {
    & $taskAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw 'QA emulator command failed' }
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb emu avd name)[0].Trim() -ne 'ofice_qa') {
    throw 'Only the disposable ofice_qa emulator may be recorded.'
}
New-Item -ItemType Directory -Force $taskOutput | Out-Null
$taskSource = Join-Path $taskOutput 'demo.mp4'
if (!(Test-Path $taskSource)) {
    Push-Location $taskRoot
    try {
        & python -c 'import imageio_ffmpeg,subprocess,sys; subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),"-y","-hide_banner","-loglevel","error","-stream_loop","11","-i","app/src/androidTest/assets/videos/cancel.mp4","-t","180","-c","copy","-movflags","+faststart",sys.argv[1]],check=True)' $taskSource
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic demo fixture creation failed' }
    } finally { Pop-Location }
}
# The synthetic long fixture stays outside the app/test APK and Git. No real user media.
Adb push $taskSource /data/local/tmp/birlevo-fgs-demo.mp4 | Out-Null
Adb shell run-as studio.birlevo.android.test mkdir -p files
Adb shell run-as studio.birlevo.android.test cp /data/local/tmp/birlevo-fgs-demo.mp4 files/demo.mp4
Adb shell pm grant studio.birlevo.android android.permission.POST_NOTIFICATIONS
Adb shell settings put system font_scale 1.0
Adb shell cmd uimode night no
Adb shell am start -n studio.birlevo.android/com.example.contentstudio.MainActivity | Out-Null
$taskRecord = Join-Path $taskOutput 'screenrecord.log'
$taskExisting = ((& $taskAdb -s $Serial shell pidof screenrecord) -join '').Trim()
if ($taskExisting) { throw 'Another QA screen recording is already active' }
$taskProcess = Start-Process -FilePath $taskAdb -ArgumentList @('-s', $Serial, 'shell', 'screenrecord', '--time-limit', '120', '--bit-rate', '4000000', '/sdcard/birlevo-fgs-demo.mp4') -PassThru -WindowStyle Hidden -RedirectStandardOutput $taskRecord -RedirectStandardError (Join-Path $taskOutput 'screenrecord-error.log')
try {
    Start-Sleep -Seconds 1
    & (Join-Path $taskRoot 'tools/release/verify-output-layouts.ps1') -Version $Version -Serial $Serial -Modes phone -Workflows fgs-demo -SkipInitialRestore
} finally {
    # There was no existing recording before ours. Stop only this guarded QA recording.
    $taskPid = ((& $taskAdb -s $Serial shell pidof screenrecord) -join '').Trim()
    if ($taskPid -match '^\d+$') { & $taskAdb -s $Serial shell kill -2 $taskPid | Out-Null }
    $taskProcess.WaitForExit(10000) | Out-Null
    Adb pull /sdcard/birlevo-fgs-demo.mp4 (Join-Path $taskOutput 'birlevo-0.1.9-foreground-demo.mp4') | Out-Null
    # Explicit generated paths only; never erase application/project data.
    Adb shell run-as studio.birlevo.android.test rm -f files/demo.mp4
    Adb shell rm -f /data/local/tmp/birlevo-fgs-demo.mp4
}
Write-Output "Demo video: $taskOutput/birlevo-0.1.9-foreground-demo.mp4"
