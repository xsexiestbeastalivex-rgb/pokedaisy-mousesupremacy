[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $AdbPath,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.:%\[\]-]*$')][string] $Serial,
    [Parameter(Mandatory)][string] $ApkPath,
    [Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string] $ExpectedSha256
)

$ErrorActionPreference = 'Stop'
$devPackage = 'com.pokedaisy.app.dev'
$officialPackage = 'com.pokedaisy.app'
$localHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ApkPath).Hash
if ($localHash -ne $ExpectedSha256) { throw 'Local dev APK SHA-256 differs from the verified artifact.' }

function Invoke-TargetAdb {
    param([string[]] $Arguments)
    $stdout = @(& $AdbPath -s $Serial @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "ADB failed on the selected target ($($Arguments -join ' '))." }
    return $stdout
}

function Get-InstalledBase {
    param([string] $Package)
    $paths = @(Invoke-TargetAdb -Arguments @('shell', 'pm', 'path', $Package))
    if ($paths.Count -ne 1 -or $paths[0] -notmatch '^package:(?<base>/data/app/[A-Za-z0-9_.=~+/-]+/base\.apk)$' -or
        $paths[0] -match '/\.\.?(/|$)') {
        throw "Expected one unambiguous installed base.apk for $Package."
    }
    return $Matches['base']
}

$state = @(Invoke-TargetAdb -Arguments @('get-state'))
if ($state.Count -ne 1 -or $state[0].Trim() -cne 'device') { throw 'Selected Android target is unavailable.' }

$basePath = Get-InstalledBase $devPackage
$line = @(Invoke-TargetAdb -Arguments @('shell', 'sha256sum', $basePath))
if ($line.Count -ne 1 -or $line[0] -notmatch '^(?<hash>[A-Fa-f0-9]{64})\s+(?<path>\S+)$' -or
    $Matches['path'] -cne $basePath) { throw 'Could not verify the installed base.apk digest.' }
if ($Matches['hash'] -ne $ExpectedSha256) {
    throw "Installed dev APK SHA-256 mismatch. Expected $($ExpectedSha256.ToLowerInvariant()), got $($Matches['hash'].ToLowerInvariant())."
}
if ((Get-InstalledBase $devPackage) -cne $basePath) { throw 'Dev installation changed during verification.' }

$officialPaths = @(Invoke-TargetAdb -Arguments @('shell', 'pm', 'path', $officialPackage))
$officialBases = @($officialPaths | Where-Object { $_ -cmatch '^package:/data/app/.+/base\.apk$' })
if ($officialBases.Count -ne 1 -or $officialBases[0] -ceq "package:$basePath") {
    throw 'The official and dev packages could not be confirmed as separate installations.'
}

$uid = @(Invoke-TargetAdb -Arguments @('shell', 'run-as', $devPackage, 'id', '-u'))
if ($uid.Count -ne 1 -or $uid[0] -notmatch '^[1-9][0-9]{4,}$') {
    throw 'run-as did not return a dev application UID.'
}

[pscustomobject]@{
    Target = $Serial
    Package = $devPackage
    InstalledBase = $basePath
    SHA256 = $ExpectedSha256.ToLowerInvariant()
    OfficialPackagePresent = $true
    RunAsAvailable = $true
}
