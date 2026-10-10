param(
    [string]$Version = '0.1.7',
    [string]$Serial = 'emulator-5554',
    [ValidateSet('image','video','pdf','transfer','formats','photos','support','video-targets','audio','documents','reader','pages','fgs-demo','expanded-audio','video-edit','mp3-source','native-mp3','pptx','slideshow','presentation-mode')][string[]]$Workflows,
    [switch]$KeepQaData
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$releaseMetadata = Get-Content -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\release\output-metadata.json') -Raw | ConvertFrom-Json
if ($releaseMetadata.elements[0].versionName -ne $Version) { throw 'Version must match the built release APK.' }
Push-Location $projectRoot
try {
    & .\gradlew.bat assembleDebugAndroidTest '-Pbirlevo.platformSmoke=true' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the independent platform smoke harness.' }
} finally { Pop-Location }
$releaseAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$apkSigner = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\36.0.0\apksigner.bat'
if ($Serial -notmatch '^emulator-\d+$' -or ((& $releaseAdb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim() -ne '1') {
    throw 'Release smoke tests reset app data and are restricted to a dedicated emulator.'
}
if ((& $releaseAdb -s $Serial emu avd name)[0].Trim() -ne 'ofice_qa') {
    throw 'Only the ofice_qa emulator may have its QA application data reset.'
}
$qaDirectory = Join-Path $projectRoot "output\development\$Version\release-smoke"
New-Item -ItemType Directory -Force $qaDirectory | Out-Null
$testApk = Join-Path $qaDirectory 'release-signed-test.apk'
$signingDirectory = Join-Path $env:LOCALAPPDATA 'Birlevo\signing'
$credential = Import-Clixml -LiteralPath (Join-Path $signingDirectory 'upload-credential.clixml')
$oldPassword = [Environment]::GetEnvironmentVariable('BIRLEVO_QA_KEY_PASSWORD')
try {
    $env:BIRLEVO_QA_KEY_PASSWORD = $credential.GetNetworkCredential().Password
    & $apkSigner sign --ks (Join-Path $signingDirectory 'birlevo-upload.p12') --ks-key-alias $credential.UserName `
        --ks-pass env:BIRLEVO_QA_KEY_PASSWORD --key-pass env:BIRLEVO_QA_KEY_PASSWORD --out $testApk `
        (Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Could not sign the smoke test harness.' }
} finally {
    [Environment]::SetEnvironmentVariable('BIRLEVO_QA_KEY_PASSWORD', $oldPassword)
    $credential.Password.Dispose()
}
function RunAdb {
    & $releaseAdb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "QA emulator command failed: $args" }
}
RunAdb shell wm size reset
RunAdb shell wm density reset
RunAdb shell settings put system font_scale 1.0
RunAdb shell cmd uimode night no
# The debug target and harness have another signing certificate. Remove only their QA copies.
if (!$KeepQaData) {
    & $releaseAdb -s $Serial uninstall studio.birlevo.android.test | Out-Null
    & $releaseAdb -s $Serial uninstall studio.birlevo.android | Out-Null
}
RunAdb install -r (Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk')
RunAdb install -r $testApk
if ($Workflows) {
    foreach ($workflow in $Workflows) {
        $workflowLog = Join-Path $qaDirectory "$workflow.log"
        RunAdb shell timeout 300 am instrument -w -r -e workflow $workflow studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> $workflowLog
        if ((Get-Content $workflowLog -Raw) -notmatch "OK \(1 $workflow workflow\)") {
            throw "Minified release workflow failed: $workflow; inspect $workflowLog"
        }
        Write-Output "Signed release workflow passed: $workflow"
    }
    return
}
RunAdb shell timeout 1200 am instrument -w -r studio.birlevo.android.test/com.example.contentstudio.ReleaseSmokeInstrumentation *> (Join-Path $qaDirectory 'instrumentation.log')
$workflowCount = if ($releaseMetadata.elements[0].versionCode -ge 12) { 18 } elseif ($releaseMetadata.elements[0].versionCode -ge 11) { 15 } elseif ($releaseMetadata.elements[0].versionCode -ge 10) { 12 } elseif ($releaseMetadata.elements[0].versionCode -ge 9) { 7 } elseif ($releaseMetadata.elements[0].versionCode -ge 8) { 6 } else { 4 }
if ((Get-Content (Join-Path $qaDirectory 'instrumentation.log') -Raw) -notmatch "OK \($workflowCount release workflows\)") {
    throw "Minified release smoke tests failed; inspect $qaDirectory\instrumentation.log"
}
Write-Output "All $workflowCount black-box workflows passed against the exact signed release APK, including output transfer and sharing."
