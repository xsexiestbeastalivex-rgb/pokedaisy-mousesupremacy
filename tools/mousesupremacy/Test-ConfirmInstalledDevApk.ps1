$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('pokedaisy-installed-check-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $fixture | Out-Null
try {
    $newApk = Join-Path $fixture 'current.apk'
    $oldApk = Join-Path $fixture 'retained.apk'
    $fake = Join-Path $fixture 'fake-adb.ps1'
    [IO.File]::WriteAllText($newApk, 'corrected verified artifact')
    [IO.File]::WriteAllText($oldApk, 'older dev installation with the same package and version')
    $expected = (Get-FileHash $newApk -Algorithm SHA256).Hash
    $env:MS_CHECK_EXPECTED = $expected
    $env:MS_CHECK_OLD = (Get-FileHash $oldApk -Algorithm SHA256).Hash
    @'
if ($args[0] -cne '-s' -or $args[1] -cne '127.0.0.1:58526') { throw 'ADB was not scoped to the exact target' }
$command = $args[2..($args.Count - 1)] -join ' '
$base = '/data/app/~~abc==/com.pokedaisy.app.dev-abc==/base.apk'
switch -Exact ($command) {
    'get-state' { 'device' }
    'shell pm path com.pokedaisy.app.dev' {
        "package:$base"
        if ($env:MS_CHECK_CASE -eq 'split') { 'package:/data/app/abc/split_config.apk' }
    }
    "shell sha256sum $base" {
        $hash = if ($env:MS_CHECK_CASE -eq 'retained') { $env:MS_CHECK_OLD } else { $env:MS_CHECK_EXPECTED }
        "$hash  $base"
    }
    'shell pm path com.pokedaisy.app' {
        if ($env:MS_CHECK_CASE -ne 'no-official') { 'package:/data/app/official/base.apk' }
    }
    'shell run-as com.pokedaisy.app.dev id -u' {
        if ($env:MS_CHECK_CASE -eq 'no-run-as') { exit 1 }
        '10126'
    }
    default { throw "Unexpected ADB command $command" }
}
exit 0
'@ | Set-Content -LiteralPath $fake -Encoding UTF8

    $verify = Join-Path $PSScriptRoot 'Confirm-InstalledDevApk.ps1'
    function Confirm-Case {
        param([string] $Case, [string] $Message, [string] $PackageFile = $newApk)
        $env:MS_CHECK_CASE = $Case
        $failure = $null
        try {
            $result = & $verify -AdbPath $fake -Serial '127.0.0.1:58526' -ApkPath $PackageFile -ExpectedSha256 $expected
        } catch { $failure = $_.Exception.Message }
        if ($Message) {
            if (!$failure -or !$failure.Contains($Message)) { throw "$Case produced '$failure' instead of '$Message'" }
        } elseif ($failure -or $result.SHA256 -ne $expected -or !$result.OfficialPackagePresent -or !$result.RunAsAvailable) {
            throw "Matching dev APK did not pass ($failure)"
        }
        Write-Output "PASS installed dev APK $Case"
    }
    Confirm-Case 'match'
    Confirm-Case 'retained' 'Installed dev APK SHA-256 mismatch'
    Confirm-Case 'wrong-local' 'Local dev APK SHA-256 differs' $oldApk
    Confirm-Case 'split' 'Expected one unambiguous installed base.apk'
    Confirm-Case 'no-official' 'could not be confirmed as separate'
    Confirm-Case 'no-run-as' 'ADB failed on the selected target'
} finally {
    Remove-Item Env:MS_CHECK_CASE,Env:MS_CHECK_EXPECTED,Env:MS_CHECK_OLD -ErrorAction SilentlyContinue
    Get-ChildItem -LiteralPath $fixture -File | Remove-Item
    Remove-Item -LiteralPath $fixture
}
