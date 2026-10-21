param(
    [string] $Jbmc = "jbmc"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

Push-Location $Root
try {
    mvn -q test-compile
    if ($LASTEXITCODE -ne 0) { throw "Maven compilation failed; JBMC was not started." }

    if (-not (Get-Command $Jbmc -ErrorAction SilentlyContinue)) {
        throw "JBMC executable '$Jbmc' was not found in PATH."
    }

    $Classpath = @(
        "bank-application\target\test-classes",
        "bank-application\target\classes",
        "formal-verification-core\target\classes"
    ) -join [IO.Path]::PathSeparator

    & $Jbmc --classpath $Classpath com.example.bank.verification.BankOperationVerificationHarness
    if ($LASTEXITCODE -ne 0) { throw "JBMC verification failed (exit $LASTEXITCODE)." }
}
finally {
    Pop-Location
}
