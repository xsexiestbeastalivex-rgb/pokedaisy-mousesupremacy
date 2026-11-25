# Run on the Linux hosted worker with PowerShell and Python. No device is contacted.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-AppxPackage([string]$Pattern) {
    if ($Pattern -ne '*WindowsSubsystemForAndroid*') { throw 'Unexpected host package query' }
    [pscustomobject]@{Version = [version]'2407.40000.4.0'}
}

function Reject([scriptblock]$Operation, [string]$Pattern) {
    try {
        & $Operation
    } catch {
        if ($_.Exception.Message -notmatch $Pattern) { throw }
        Write-Output "PASS runner rejected $env:PROBE_RUNNER_SCENARIO"
        return
    }
    throw "Runner unexpectedly accepted $env:PROBE_RUNNER_SCENARIO"
}

$fixture = Join-Path ([IO.Path]::GetTempPath()) ("probe-runner-" + [guid]::NewGuid().ToString('N'))
$null = New-Item -Path $fixture -ItemType Directory
$env:PROBE_RUNNER_FIXTURE = $fixture
try {
    $script = Join-Path $fixture 'Test-WsaPointerProbe.ps1'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../Test-WsaPointerProbe.ps1') -Destination $script
    $apk = Join-Path $fixture 'mousesupremacy-pointer-probe.apk'
    $sidecar = Join-Path $fixture 'probe-build.json'
    $trusted = 'trusted test bytes'
    [IO.File]::WriteAllText($apk, $trusted)
    $digest = (Get-FileHash -LiteralPath $apk).Hash.ToLowerInvariant()
    $commit = ('0123456789abcdef' * 2) + '01234567'
    $metadata = [ordered]@{
        schemaVersion = 1
        sourceCommit = $commit
        package = 'com.pokedaisy.mousesupremacy.probe'
        activity = 'com.pokedaisy.mousesupremacy.probe.ProbeActivity'
        apk = 'mousesupremacy-pointer-probe.apk'
        signatureScheme = 'v2'
        sha256 = $digest
        signerSha256 = 'a' * 64
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $sidecar
    $target = @{ Serial = '127.0.0.1:55555'; Adb = (Join-Path $PSScriptRoot 'mock-adb.py') }
    $pin = @{ ExpectedCommit = $commit; ExpectedApkSha256 = $digest }
    $run = Join-Path $fixture 'successful run'
    $env:PROBE_RUNNER_SCENARIO = 'success'
    $null = & $script @target @pin -Action Prepare -RunDirectory $run -WsaCompatibility
    $null = & $script @target -Action Collect -RunDirectory $run
    $zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $run 'pointer-evidence.zip'))
    try {
        if (($zip.Entries.Name | Sort-Object) -join ',' -ne 'pointer-trace.json,run.json') {
            throw 'Unexpected evidence bundle files'
        }
    } finally { $zip.Dispose() }
    $runInfo = Get-Content -Raw -LiteralPath (Join-Path $run 'run.json')
    if ($runInfo -match [regex]::Escape($target.Serial)) { throw 'Serial leaked into run metadata' }
    if (($runInfo | ConvertFrom-Json).inputProfile -ne 'wsa-experimental') { throw 'Wrong interpretation profile' }
    Write-Output 'PASS scoped Prepare and Collect with pinned artifact and WSA profile'

    $bad = Join-Path $fixture 'must not install'
    $env:PROBE_RUNNER_SCENARIO = 'wrong-guest'
    Reject { & $script @target @pin -Action Prepare -RunDirectory $bad } 'does not identify as a Microsoft WSA guest'
    $env:PROBE_RUNNER_SCENARIO = 'unauthorized'
    Reject { & $script @target @pin -Action Prepare -RunDirectory $bad } 'unauthorized'
    $env:PROBE_RUNNER_SCENARIO = 'missing-pin'
    Reject { & $script @target -ExpectedCommit $commit -Action Prepare -RunDirectory $bad } 'independently published'
    $env:PROBE_RUNNER_SCENARIO = 'tampered-apk'
    [IO.File]::WriteAllText($apk, 'different bytes')
    Reject { & $script @target @pin -Action Prepare -RunDirectory $bad } 'SHA-256 mismatch'
    $env:PROBE_RUNNER_SCENARIO = 'tampered-apk-and-sidecar'
    $metadata.sha256 = (Get-FileHash -LiteralPath $apk).Hash.ToLowerInvariant()
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $sidecar
    Reject { & $script @target @pin -Action Prepare -RunDirectory $bad } 'does not identify the expected'
    [IO.File]::WriteAllText($apk, $trusted)
    $metadata.sha256 = $digest
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $sidecar

    $env:PROBE_RUNNER_SCENARIO = 'signer'
    Reject { & $script @target @pin -Action Prepare -RunDirectory $bad } 'install failed'
    if (Test-Path -LiteralPath $bad) { throw 'Failed Prepare created run state' }
    $env:PROBE_RUNNER_SCENARIO = 'stale'
    Reject { & $script @target -Action Collect -RunDirectory $run } 'different Prepare session'
    foreach ($scenario in @('truncated', 'missing-layout')) {
        $env:PROBE_RUNNER_SCENARIO = $scenario
        Reject { & $script @target -Action Collect -RunDirectory $run } 'Incomplete capture'
        if (-not (Test-Path -LiteralPath (Join-Path $run 'pointer-trace-incomplete.json'))) {
            throw 'Incomplete diagnostics were not retained locally'
        }
    }

    $calls = @(Get-Content -LiteralPath (Join-Path $fixture 'adb.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    if (@($calls | Where-Object { $_.args -contains 'install' }).Count -ne 2 -or
        @($calls | Where-Object { $_.args -contains 'am' }).Count -ne 1) {
        throw 'Wrong guest, edited artifact or failed install could launch an app'
    }
    Write-Output 'PASS package scope and no launch after failed install'
} finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force
}
