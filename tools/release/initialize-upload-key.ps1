param(
    [string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'Birlevo\signing'),
    [string]$BackupDirectory = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Birlevo-signing-backup')
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This script requires Windows DPAPI.' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
foreach ($directory in @($SigningDirectory, $BackupDirectory)) {
    $absolute = [IO.Path]::GetFullPath($directory)
    if ($absolute.Equals($projectRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $absolute.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Signing material must stay outside the project.'
    }
    if (Test-Path -LiteralPath $absolute) { throw "Directory already exists; existing keys will not be overwritten: $absolute" }
}
$keytool = (Get-Command keytool -ErrorAction Stop).Source

function New-PrivateDirectory([string]$Path) {
    $directory = New-Item -ItemType Directory -Path $Path
    $permissions = New-Object Security.AccessControl.DirectorySecurity
    $permissions.SetAccessRuleProtection($true, $false)
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $permissions.SetOwner($owner)
    foreach ($sid in @($owner, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $sid, 'FullControl', 'ContainerInherit, ObjectInherit', 'None', 'Allow')
        $permissions.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $directory.FullName -AclObject $permissions
}

New-PrivateDirectory $SigningDirectory
New-PrivateDirectory $BackupDirectory
$keyPath = Join-Path $SigningDirectory 'birlevo-upload.p12'
$credentialPath = Join-Path $SigningDirectory 'upload-credential.clixml'
$certificatePath = Join-Path $SigningDirectory 'upload-certificate.pem'
$alias = 'birlevo-upload'
$randomBytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($randomBytes)
$random.Dispose()
$password = [Convert]::ToBase64String($randomBytes)
[Array]::Clear($randomBytes, 0, $randomBytes.Length)
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
$credential = New-Object Management.Automation.PSCredential($alias, $securePassword)
# Windows Export-Clixml encrypts the password for this Windows user via DPAPI.
$credential | Export-Clixml -LiteralPath $credentialPath
$previousPassword = [Environment]::GetEnvironmentVariable('BIRLEVO_KEY_CREATION_PASSWORD')
try {
    $env:BIRLEVO_KEY_CREATION_PASSWORD = $password
    & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 `
        -storepass:env BIRLEVO_KEY_CREATION_PASSWORD -keypass:env BIRLEVO_KEY_CREATION_PASSWORD `
        -alias $alias -keyalg RSA -keysize 3072 -sigalg SHA256withRSA `
        -validity 10000 -dname 'CN=Birlevo Upload' -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed.' }
    & $keytool -exportcert -rfc -keystore $keyPath -alias $alias `
        -storepass:env BIRLEVO_KEY_CREATION_PASSWORD -file $certificatePath
    if ($LASTEXITCODE -ne 0) { throw 'Public certificate export failed.' }
    & $keytool -list -v -keystore $keyPath -alias $alias -storepass:env BIRLEVO_KEY_CREATION_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Upload key verification failed.' }
} finally {
    [Environment]::SetEnvironmentVariable('BIRLEVO_KEY_CREATION_PASSWORD', $previousPassword)
    $password = $null
    $securePassword.Dispose()
}

$recoveryNote = @'
Birlevo upload key - PRIVATE MATERIAL

birlevo-upload.p12 is the password-protected private upload key. Keep it secret.
upload-certificate.pem is public and may be used in Google Play Console.
upload-credential.clixml encrypts the alias/password with Windows DPAPI.
It can only be decrypted by the same Windows user on this computer.

The backup here is a second local copy, NOT an independent disaster-recovery backup.
Before reinstalling Windows or changing computers, retrieve the password with
Import-Clixml and PSCredential.GetNetworkCredential() in a private local session,
save it in your password manager, and copy the .p12 to separate protected storage.
Never paste the password into a chat, commit it to Git, or put it in build logs.

Build from the project with tools/release/build-signed-release.ps1.
Future versions must reuse this upload key; do not generate a replacement casually.
'@
$recoveryNote | Set-Content -LiteralPath (Join-Path $SigningDirectory 'RECOVERY.txt') -Encoding UTF8
foreach ($fileName in @('birlevo-upload.p12', 'upload-credential.clixml', 'upload-certificate.pem', 'RECOVERY.txt')) {
    Copy-Item -LiteralPath (Join-Path $SigningDirectory $fileName) -Destination (Join-Path $BackupDirectory $fileName)
}
if ((Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath (Join-Path $BackupDirectory 'birlevo-upload.p12') -Algorithm SHA256).Hash) {
    throw 'Backup checksum mismatch.'
}
Write-Output "Upload key created and local backup verified. Signing directory: $SigningDirectory"
Write-Output "Local backup: $BackupDirectory"
