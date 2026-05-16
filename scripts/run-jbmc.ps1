param(
    [string] $Jbmc = "jbmc"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

Push-Location $Root
try {
    mvn -q test-compile

    if (-not (Get-Command $Jbmc -ErrorAction SilentlyContinue)) {
        throw "JBMC executable '$Jbmc' was not found in PATH."
    }

    $Classpath = @(
        "bank-application\target\test-classes",
        "bank-application\target\classes",
        "formal-verification-core\target\classes"
    ) -join [IO.Path]::PathSeparator

    & $Jbmc --classpath $Classpath com.example.bank.verification.BankOperationVerificationHarness
}
finally {
    Pop-Location
}
