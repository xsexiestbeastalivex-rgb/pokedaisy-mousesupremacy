[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare','Collect')][string]$Action,
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$RunDirectory,
    [string]$Adb = 'C:\platform-tools\adb.exe',
    [string]$BuildDirectory = $PSScriptRoot,
    [ValidatePattern('^[0-9a-f]{40}$')][string]$ExpectedCommit,
    [ValidateSet('physical-wsa','physical-android','injected')][string]$Provenance = 'physical-wsa',
    [ValidateSet('windowed','maximized','stock-android','emulator','other')][string]$WindowMode = 'windowed',
    [switch]$Connect,
    [switch]$Screenshot,
    [string]$OperatorNotes,
    [string]$Mouse,
    [string]$DisplayScaling
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$package = 'com.pokedaisy.mousesupremacy.probe'
$activity = "$package.ProbeActivity"
$encoding = New-Object System.Text.UTF8Encoding($false)
$RunDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($RunDirectory)
$statePath = Join-Path $RunDirectory 'run.json'

function Invoke-Adb([string[]]$Arguments) {
    $lines = & $Adb @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed with exit code $LASTEXITCODE." }
    return ($lines -join "`n")
}

function Invoke-Target([string[]]$Arguments) {
    return (Invoke-Adb (@('-s', $Serial) + $Arguments))
}

function Save-Json([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 12) + "`n"), $encoding)
}

if ($Serial -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.:\[\]%-]{0,255}$') { throw 'Invalid ADB target format.' }
if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) { throw "ADB executable missing at $Adb." }
$escapedSerial = [regex]::Escape($Serial)
$devices = Invoke-Adb @('devices','-l')
if ($devices -notmatch "(?m)^$escapedSerial\s+\S+" -and $Connect) {
    if ($Serial -notmatch ':\d+$') { throw 'Connect requires the exact TCP endpoint from WSA Developer Mode.' }
    $null = Invoke-Adb @('connect', $Serial)
    $devices = Invoke-Adb @('devices','-l')
}
if ($devices -notmatch "(?m)^$escapedSerial\s+device(?:\s|$)" -or
    (Invoke-Target @('get-state')).Trim() -ne 'device') {
    throw 'The specified target is absent, offline or unauthorized. Verify WSA Developer Mode and ADB authorization.'
}

if ($Action -eq 'Prepare') {
    if (-not $ExpectedCommit) { throw 'Supply the exact trusted APK source commit with -ExpectedCommit.' }
    if (Test-Path -LiteralPath $RunDirectory) { throw 'Choose a new run directory for each session.' }
    $metadata = Get-Content -Raw -LiteralPath (Join-Path $BuildDirectory 'probe-build.json') | ConvertFrom-Json
    if ($metadata.schemaVersion -ne 1 -or $metadata.sourceCommit -cne $ExpectedCommit -or
        $metadata.package -cne $package -or $metadata.activity -cne $activity -or
        $metadata.apk -cne 'mousesupremacy-pointer-probe.apk' -or $metadata.signatureScheme -cne 'v2' -or
        $metadata.sha256 -cnotmatch '^[0-9a-f]{64}$' -or $metadata.signerSha256 -cnotmatch '^[0-9a-f]{64}$') {
        throw 'Trusted build metadata does not identify the expected standalone signed probe.'
    }
    $apk = Join-Path $BuildDirectory $metadata.apk
    if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -cne $metadata.sha256) {
        throw 'APK SHA-256 mismatch. No APK was installed.'
    }
    $api = (Invoke-Target @('shell','getprop','ro.build.version.sdk')).Trim()
    if ($api -notmatch '^\d+$' -or [int]$api -lt 26) { throw 'The selected guest requires Android API 26 or newer.' }
    $abis = (Invoke-Target @('shell','getprop','ro.product.cpu.abilist')).Trim()
    $wsaVersion = @()
    if ($Provenance -eq 'physical-wsa') {
        $wsaVersion = @(Get-AppxPackage '*WindowsSubsystemForAndroid*' | ForEach-Object { $_.Version.ToString() })
        if ($wsaVersion.Count -eq 0) { throw 'The existing WSA package was not found on this Windows host.' }
        if ($WindowMode -notin @('windowed','maximized')) { throw 'Select windowed or maximized for WSA.' }
    }
    $session = [guid]::NewGuid().ToString('N')
    $installed = Invoke-Target @('install','-r',$apk)
    if ($installed -notmatch '(?m)^Success\s*$') { throw 'Standalone probe install did not report success.' }
    $installedPath = Invoke-Target @('shell','pm','path',$package)
    if ($installedPath -notmatch '(?m)^package:') { throw 'Standalone package is missing after install.' }
    $launch = Invoke-Target @('shell','am','start','-W','-S','-n',"$package/$activity",
        '--es','provenance',$Provenance,'--es','session',$session)
    if ($launch -notmatch '(?m)^Status:\s+ok\s*$') { throw "Probe launch did not report success. $launch" }
    $null = New-Item -ItemType Directory -Path $RunDirectory
    Save-Json $statePath ([ordered]@{
        schemaVersion = 1
        session = $session
        provenance = $Provenance
        windowMode = $WindowMode
        windowsVersion = [Environment]::OSVersion.Version.ToString()
        wsaVersion = $wsaVersion
        androidApi = [int]$api
        androidAbis = $abis
        mouse = $Mouse
        displayScaling = $DisplayScaling
        preparedUtc = [DateTime]::UtcNow.ToString('o')
        build = $metadata
    })
    Write-Output "Probe launched. Focus it with the keyboard, use F8 to start and stop, then run Collect for $RunDirectory."
    return
}

$state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
$json = Invoke-Target @('exec-out','run-as',$package,'cat','files/pointer-trace.json')
if ([string]::IsNullOrWhiteSpace($json)) { throw 'No probe export. Use F8 to stop and wait for Export ready.' }
$trace = $json | ConvertFrom-Json
if ($trace.schemaVersion -ne 1 -or $trace.session -cne $state.session -or
    $trace.provenance -cne $state.provenance -or $trace.sourceCommit -cne $state.build.sourceCommit -or
    $trace.capacity -ne 4096 -or $trace.events.Count -lt 2 -or $trace.events.Count -gt $trace.capacity -or
    $trace.events[-1].kind -cne 'end') {
    throw 'Trace is invalid or belongs to a different Prepare session. Capture and export on the intended target.'
}
$tracePath = Join-Path $RunDirectory 'pointer-trace.json'
[IO.File]::WriteAllText($tracePath, ($json + "`n"), $encoding)
$files = @($statePath, $tracePath)
if ($OperatorNotes) {
    $notesPath = Join-Path $RunDirectory 'operator-notes.txt'
    Copy-Item -LiteralPath $OperatorNotes -Destination $notesPath
    $files += $notesPath
}
if ($Screenshot) {
    $devicePath = '/data/local/tmp/mousesupremacy-probe-' + [guid]::NewGuid().ToString('N') + '.png'
    $screenshotPath = Join-Path $RunDirectory 'guest-after-export.png'
    try {
        $null = Invoke-Target @('shell','screencap','-p',$devicePath)
        $null = Invoke-Target @('pull',$devicePath,$screenshotPath)
    } finally {
        $null = Invoke-Target @('shell','rm','-f',$devicePath)
    }
    $files += $screenshotPath
}
$zip = Join-Path $RunDirectory 'pointer-evidence.zip'
Compress-Archive -LiteralPath $files -DestinationPath $zip -Force
Write-Output "Evidence collected at $zip. Review screenshots and notes before sharing."
