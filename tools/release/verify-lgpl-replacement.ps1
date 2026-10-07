param([Parameter(Mandatory)][string]$Version, [string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function Adb {
    & $taskAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "QA command failed: $args" }
}
if ($Serial -notmatch '^emulator-\d+$' -or ((Adb shell getprop ro.kernel.qemu) -join '').Trim() -ne '1' -or
    (Adb emu avd name)[0].Trim() -ne 'ofice_qa') { throw 'Only the dedicated ofice_qa emulator may be reset.' }
$inspection = Get-Content -LiteralPath (Join-Path $taskRoot "output/releases/$Version/inspection.json") -Raw | ConvertFrom-Json
if ($inspection.versionName -ne $Version) { throw 'Version mismatch.' }
$taskApk = Join-Path $taskRoot "output/development/$Version/birlevo-$Version-$($inspection.versionCode)-release.apk"
$taskHarness = Join-Path $taskRoot "output/development/$Version/release-smoke/release-signed-test.apk"
$taskDebugHarness = Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$taskWork = Join-Path $taskRoot "output/development/$Version/native-replacement-final"
foreach ($taskFile in @($taskApk, $taskHarness, $taskDebugHarness)) {
    if (!(Test-Path -LiteralPath $taskFile -PathType Leaf)) { throw "Missing QA artifact: $taskFile" }
}
Push-Location $taskRoot
try {
    & python tools/release/prepare_lgpl_replacement.py $taskApk --work $taskWork
    if ($LASTEXITCODE -ne 0) { throw 'Source rebuild / QA-only APK preparation failed.' }
    $targetChanged = $false
    try {
        # Different customer signer: remove only the emulator's disposable app/harness.
        $targetChanged = $true
        & $taskAdb -s $Serial uninstall studio.birlevo.android.test | Out-Null
        & $taskAdb -s $Serial uninstall studio.birlevo.android | Out-Null
        Adb install (Join-Path $taskWork 'replacement-qa-only.apk')
        Adb install $taskDebugHarness
        Adb shell timeout 300 am instrument -w -r -e workflow expanded-audio -e lameVersion '4.0-birlevo-qa-replacement' `
            studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $taskWork 'runtime.log')
        if ((Get-Content -LiteralPath (Join-Path $taskWork 'runtime.log') -Raw) -notmatch 'OK \(1 expanded-audio workflow\)') {
            throw 'Modified encoder failed preserved ABI / real GUI output tests.'
        }
        $taskOutputs = Join-Path $taskWork 'runtime'
        New-Item -ItemType Directory -Force $taskOutputs | Out-Null
        Adb pull /sdcard/Android/data/studio.birlevo.android/files/qa $taskOutputs | Out-Null
        & python tools/release/verify_release_media.py (Join-Path $taskOutputs 'qa') --audio-only --report (Join-Path $taskWork 'independent-decode.json')
        if ($LASTEXITCODE -ne 0) { throw 'Independent modified-encoder output decoding failed.' }
        @{ passed = $true; qaOnly = $true; version = $Version;
           originalApkSha256 = (Get-FileHash $taskApk).Hash.ToLowerInvariant();
           runtimeWorkflow = 'expanded-audio'; expectedNativeVersion = '4.0-birlevo-qa-replacement';
           customerSigner = 'local Android debug key'; independentAudioFiles = 4
        } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskWork 'verification.json') -Encoding utf8
    } finally {
        if ($targetChanged) {
            & $taskAdb -s $Serial uninstall studio.birlevo.android.test | Out-Null
            & $taskAdb -s $Serial uninstall studio.birlevo.android | Out-Null
            Adb install $taskApk
            Adb install $taskHarness
        }
    }
    Adb shell timeout 60 am instrument -w -r -e workflow native-mp3 -e lameVersion '4.0' `
        studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $taskWork 'official-restored.log')
    if ((Get-Content -LiteralPath (Join-Path $taskWork 'official-restored.log') -Raw) -notmatch 'OK \(1 native-mp3 workflow\)') {
        throw 'Official APK native ABI restoration failed.'
    }
    Write-Output 'Offline source rebuild, customer-signed encoder replacement, R8 GUI outputs and official APK restoration passed.'
} finally { Pop-Location }
