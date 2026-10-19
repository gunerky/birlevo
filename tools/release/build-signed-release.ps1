param(
    [string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'Birlevo\signing'),
    [switch]$TestAds
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$keyPath = Join-Path $SigningDirectory 'birlevo-upload.p12'
$credentialPath = Join-Path $SigningDirectory 'upload-credential.clixml'
if (!(Test-Path -LiteralPath $keyPath) -or !(Test-Path -LiteralPath $credentialPath)) {
    throw 'Upload key or encrypted Windows credential is missing.'
}
$credential = Import-Clixml -LiteralPath $credentialPath
if ($credential -isnot [Management.Automation.PSCredential]) { throw 'Invalid encrypted credential.' }
$settings = @(
    'BIRLEVO_UPLOAD_KEYSTORE', 'BIRLEVO_UPLOAD_STORE_PASSWORD',
    'BIRLEVO_UPLOAD_KEY_ALIAS', 'BIRLEVO_UPLOAD_KEY_PASSWORD'
)
$previous = @{}
foreach ($name in $settings) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
Push-Location $projectRoot
try {
    $env:BIRLEVO_UPLOAD_KEYSTORE = $keyPath
    $env:BIRLEVO_UPLOAD_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:BIRLEVO_UPLOAD_KEY_ALIAS = $credential.UserName
    $env:BIRLEVO_UPLOAD_KEY_PASSWORD = $env:BIRLEVO_UPLOAD_STORE_PASSWORD
    # Do not persist signing secrets in a Gradle daemon process or property file.
    $releaseProperties = @("-Pbirlevo.releaseTestAds=$($TestAds.IsPresent.ToString().ToLowerInvariant())")
    & .\gradlew.bat bundleRelease assembleRelease lintRelease @releaseProperties --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Signed release build failed.' }
    $bundlePath = Join-Path $projectRoot 'app\build\outputs\bundle\release\app-release.aab'
    $verification = & jarsigner '-J-Duser.language=en' -verify $bundlePath
    $verification | Set-Content -LiteralPath (Join-Path $projectRoot 'app\build\reports\release-signature-verification.txt') -Encoding UTF8
    if ($LASTEXITCODE -ne 0 -or ($verification -join "`n") -notmatch 'jar verified\.') {
        throw 'AAB signature verification failed or bundle is unsigned.'
    }
    Write-Output 'jarsigner verified the AAB; detailed diagnostics are in app/build/reports/release-signature-verification.txt.'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.Security
    $pem = Get-Content -LiteralPath (Join-Path $SigningDirectory 'upload-certificate.pem') -Raw
    $expectedDer = [Convert]::FromBase64String(($pem -replace '-----[^-]+-----', '' -replace '\s', ''))
    $sha = [Security.Cryptography.SHA256]::Create()
    $expectedFingerprint = [Convert]::ToBase64String($sha.ComputeHash($expectedDer))
    $archive = [IO.Compression.ZipFile]::OpenRead($bundlePath)
    try {
        $signatures = @($archive.Entries | Where-Object { $_.FullName -match '^META-INF/[^/]+\.(RSA|DSA|EC)$' })
        if ($signatures.Count -ne 1) { throw 'Expected exactly one AAB signer.' }
        $input = $signatures[0].Open()
        $buffer = New-Object IO.MemoryStream
        try { $input.CopyTo($buffer) } finally { $input.Dispose() }
        $signature = New-Object Security.Cryptography.Pkcs.SignedCms
        try { $signature.Decode($buffer.ToArray()) } finally { $buffer.Dispose() }
        if ($signature.SignerInfos.Count -ne 1) { throw 'Unexpected signer count.' }
        $actualFingerprint = [Convert]::ToBase64String($sha.ComputeHash($signature.SignerInfos[0].Certificate.RawData))
        if ($actualFingerprint -ne $expectedFingerprint) { throw 'Bundle signer does not match the upload certificate.' }
    } finally { $archive.Dispose(); $sha.Dispose() }
    Write-Output 'AAB signature matches the Birlevo upload certificate.'
    # Only verifies/extracts symbols from this exact bundle; cannot substitute another build.
    & python tools/release/prepare_native_symbols.py $bundlePath --require-owned --output-dir app/build/outputs/native-debug-symbols/release/verified
    if ($LASTEXITCODE -ne 0) { throw 'Native symbol matching failed.' }
    & keytool -printcert -jarfile $bundlePath
    if ($LASTEXITCODE -ne 0) { throw 'AAB certificate inspection failed.' }
    Write-Output "Signed AAB: $bundlePath"
    Get-FileHash -LiteralPath $bundlePath -Algorithm SHA256 | Select-Object Algorithm, Hash, Path
} finally {
    foreach ($name in $settings) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    $credential.Password.Dispose()
    Pop-Location
}
