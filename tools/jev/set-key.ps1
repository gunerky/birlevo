param([switch]$FromEnvironment, [switch]$Replace)

$ErrorActionPreference = 'Stop'
# Use this shell's own module even when launched from a PowerShell 7 parent.
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
$secretDirectory = Join-Path $env:LOCALAPPDATA 'BirlevoDevelopment'
$credentialPath = Join-Path $secretDirectory 'typesafe-credential.clixml'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ([IO.Path]::GetFullPath($secretDirectory).StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Development credentials must remain outside the project.'
}
if ((Test-Path -LiteralPath $credentialPath) -and !$Replace) {
    throw 'A local credential already exists. Use -Replace only when you intend to change it.'
}
if ($FromEnvironment) {
    if ([string]::IsNullOrWhiteSpace($env:TYPESAFE_API_KEY)) { throw 'TYPESAFE_API_KEY is not configured.' }
    $secretValue = ConvertTo-SecureString -String $env:TYPESAFE_API_KEY -AsPlainText -Force
} else {
    $secretValue = Read-Host 'TypeSafe API key (masked; never paste it into chat)' -AsSecureString
}
if ($secretValue.Length -lt 8 -or $secretValue.Length -gt 4096) { throw 'Invalid key length.' }
New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
# Limit access to the current Windows identity and SYSTEM; DPAPI also binds the credential.
$directoryAcl = Get-Acl -LiteralPath $secretDirectory
$directoryAcl.SetAccessRuleProtection($true, $false)
$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$systemIdentity = New-Object Security.Principal.SecurityIdentifier('S-1-5-18')
foreach ($identity in @($currentIdentity, $systemIdentity)) {
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $directoryAcl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $secretDirectory -AclObject $directoryAcl
$credential = New-Object Management.Automation.PSCredential('typesafe-development', $secretValue)
$temporaryPath = Join-Path $secretDirectory 'typesafe-credential.clixml.tmp'
try {
    $credential | Export-Clixml -LiteralPath $temporaryPath -Encoding UTF8
    Move-Item -LiteralPath $temporaryPath -Destination $credentialPath -Force
} finally {
    $secretValue.Dispose()
    $credential = $null
}
Write-Output 'Development key saved outside the project, encrypted for this Windows account. No API call made.'
