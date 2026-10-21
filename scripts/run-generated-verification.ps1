param(
    [string] $Jbmc = "jbmc",
    [string] $Python = "python",
    [switch] $SkipBuild,
    [switch] $CompareManual,
    [int] $TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Push-Location $Root
try {
    if (-not $SkipBuild) {
        & mvn clean test
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed; JBMC was not started." }
    }
    $Arguments = @("scripts/verify_contracts.py", "--jbmc", $Jbmc, "--timeout", "$TimeoutSeconds")
    if ($CompareManual) { $Arguments += "--compare-manual" }
    & $Python @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Verification failed; see bank-application/target/verification-results/report.md." }
}
finally { Pop-Location }
