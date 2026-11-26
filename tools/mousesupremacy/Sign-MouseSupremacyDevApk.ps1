[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $OriginalApk,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string] $ExpectedOriginalSha256,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string] $SourceCommit,
    [Parameter(Mandatory)][string] $OutputApk,
    [Parameter(Mandatory)][string] $BuildToolsDir,
    [switch] $UnsignedLocalExperiment
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Checked([string] $Tool, [string[]] $Arguments) {
    $result = & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build tool failed ($Tool, exit $LASTEXITCODE)." }
    return $result
}

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Find-Signer([string] $Path) {
    $lines = @(Invoke-Checked java @('-jar', $apksigner, 'verify', '--verbose', '--print-certs', $Path))
    if (!($lines -ceq 'Number of signers: 1') -or
        !($lines -cmatch '^Verified using v2 scheme .*: true$') -or
        !($lines -cmatch '^Verified using v1 scheme .*: false$') -or
        ($lines -cmatch '^Verified using v[34](?:\.\d+)? scheme .*: true$')) {
        throw 'Expected one verified APK v2-only signer.'
    }
    $cert = @($lines -cmatch '^Signer #1 certificate SHA-256 digest: [a-f0-9]{64}$')
    if ($cert.Count -ne 1) { throw 'Certificate fingerprint unavailable.' }
    return $cert[0].Split(' ')[-1]
}

$original = (Get-Item -LiteralPath $OriginalApk).FullName
$output = [IO.Path]::GetFullPath($OutputApk)
$sidecar = "$output.provenance.txt"
$tools = (Get-Item -LiteralPath $BuildToolsDir).FullName
$aapt = Join-Path $tools 'aapt.exe'
$zipalign = Join-Path $tools 'zipalign.exe'
$apksigner = Join-Path $tools 'lib\apksigner.jar'
$payloadCheck = Join-Path $PSScriptRoot 'verify-apk-payload.py'
$expected = $ExpectedOriginalSha256.ToLowerInvariant()
$source = $SourceCommit.ToLowerInvariant()
if ([IO.Path]::GetExtension($output) -ine '.apk' -or (Test-Path -LiteralPath $output) -or
    (Test-Path -LiteralPath $sidecar)) { throw 'Provide a fresh .apk output path and provenance filename.' }
if ((Get-Sha256 $original) -cne $expected) { throw 'Original APK differs from the pinned digest.' }

$manifest = @(Invoke-Checked $aapt @('dump', 'badging', $original))
if (!($manifest -cmatch "^package: name='com\.pokedaisy\.app\.dev' ") -or
    !($manifest -ceq "native-code: 'arm64-v8a' 'x86_64'")) {
    throw 'Expected the isolated dev package and both native ABIs.'
}
$ciSigner = 'unsigned local experiment'
if ($UnsignedLocalExperiment) {
    $verification = @(& java '-jar' $apksigner 'verify' '--verbose' '--print-certs' $original 2>&1)
    if ($LASTEXITCODE -eq 0 -or !($verification -match 'Missing META-INF/MANIFEST.MF')) {
        throw 'The experimental input must be an unsigned APK.'
    }
} else {
    $ciSigner = Find-Signer $original
}
Invoke-Checked $zipalign @('-c', '-p', '4', $original) | Out-Null
Invoke-Checked python @($payloadCheck, $original, $original, $source) | Out-Null

# Reuse the owner-controlled signer created by the approved local migration.
$vault = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'MouseSupremacy-Private'
$backup = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'MouseSupremacy-Private-Backup'
$userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
foreach ($directory in @($vault, $backup)) {
    $folder = Get-Item -LiteralPath $directory
    $acl = Get-Acl -LiteralPath $directory
    $rules = @($acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    $owners = @($rules | ForEach-Object { $_.IdentityReference.Value })
    if (!$folder.PSIsContainer -or ($folder.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        !$acl.AreAccessRulesProtected -or $rules.Count -ne 2 -or
        $userSid -notin $owners -or 'S-1-5-18' -notin $owners -or
        @($rules | Where-Object { $_.AccessControlType -ne 'Allow' }).Count) {
        throw 'Signer directories must be restricted to the Windows owner and SYSTEM.'
    }
}
foreach ($name in @('dev-signer.p12', 'dev-signer.dpapi', 'dev-signer.sha256')) {
    $files = @((Get-Item -LiteralPath (Join-Path $vault $name)), (Get-Item -LiteralPath (Join-Path $backup $name)))
    foreach ($file in $files) {
        if ($file.PSIsContainer -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Signer material must reside in the two pinned private directories.'
        }
    }
    if ((Get-Sha256 $files[0].FullName) -cne (Get-Sha256 $files[1].FullName)) { throw 'Signer backup checksum differs.' }
}
$pin = (Get-Content -LiteralPath (Join-Path $vault 'dev-signer.sha256') -Raw).Trim()
if ($pin -cnotmatch '^[a-f0-9]{64}$' -or $pin -ceq $ciSigner) { throw 'Expected a separate pinned local signer.' }

$nonce = [Guid]::NewGuid().ToString('N')
$staged = Join-Path ([IO.Path]::GetDirectoryName($output)) ".$nonce.apk"
$stagedSidecar = "$staged.provenance.txt"
$environmentName = "MOUSESUPREMACY_LOCAL_PASS_$nonce"
$password = $null
$alias = 'mousesupremacy-local-dev'
try {
    try {
        $password = Get-Content -LiteralPath (Join-Path $vault 'dev-signer.dpapi') -Raw | ConvertTo-SecureString
        [Environment]::SetEnvironmentVariable($environmentName,
            [System.Net.NetworkCredential]::new('', $password).Password, 'Process')
        Invoke-Checked keytool @('-list', '-keystore', (Join-Path $backup 'dev-signer.p12'), '-storetype',
            'PKCS12', '-storepass:env', $environmentName, '-alias', $alias) | Out-Null
        Invoke-Checked java @('-jar', $apksigner, 'sign', '--ks', (Join-Path $vault 'dev-signer.p12'),
            '--ks-type', 'PKCS12', '--ks-key-alias', $alias, '--ks-pass', "env:$environmentName",
            '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'false',
            '--v4-signing-enabled', 'false', '--out', $staged, $original) | Out-Null
    } finally {
        [Environment]::SetEnvironmentVariable($environmentName, $null, 'Process')
        if ($password) { $password.Dispose() }
    }

    if ((Find-Signer $staged) -cne $pin) { throw 'Derived APK does not match the pinned local signer.' }
    Invoke-Checked $zipalign @('-c', '-p', '4', $staged) | Out-Null
    $derivedManifest = @(Invoke-Checked $aapt @('dump', 'badging', $staged))
    if (($manifest -join "`n") -cne ($derivedManifest -join "`n")) { throw 'Android package metadata changed.' }
    $payload = @(Invoke-Checked python @($payloadCheck, $original, $staged, $source))
    if ((Get-Sha256 $original) -cne $expected) { throw 'Original APK changed during signing.' }
    $record = @(
        "Source commit $source",
        "Original APK SHA-256 $expected",
        "Original signer certificate SHA-256 $ciSigner",
        "Derived APK SHA-256 $(Get-Sha256 $staged)",
        "Local signer certificate SHA-256 $pin",
        $(if ($UnsignedLocalExperiment) {
            'Unsigned local experimental input; output verified v2-only; both passed zipalign -c -p 4'
        } else {
            'Both APKs verified v2-only and passed zipalign -c -p 4'
        })
    ) + $payload + @($manifest -match '^package:|^native-code:')
    $record | Set-Content -LiteralPath $stagedSidecar
    [IO.File]::Move($stagedSidecar, $sidecar)
    [IO.File]::Move($staged, $output)
    $record
} finally {
    foreach ($item in @($staged, $stagedSidecar)) {
        if (Test-Path -LiteralPath $item -PathType Leaf) { Remove-Item -LiteralPath $item }
    }
}
