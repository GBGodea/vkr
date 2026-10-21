param([string] $Jbmc = "jbmc", [string] $Python = "python", [switch] $SkipBuild)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Push-Location $Root
try {
    if (-not $SkipBuild) {
        & mvn clean test
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
    }
    New-Item -ItemType Directory -Force -Path target/stage3-examples/classes,target/stage3-examples/generated | Out-Null
    $ProcessorPath = @("formal-verification-processor/target/classes", "formal-verification-core/target/classes") -join [IO.Path]::PathSeparator
    & javac --release 17 -g -encoding UTF-8 -classpath formal-verification-core/target/classes `
        -processorpath $ProcessorPath -processor ru.vkr.verification.processor.VerificationProcessor `
        -d target/stage3-examples/classes -s target/stage3-examples/generated verification-examples/Stage3Examples.java
    if ($LASTEXITCODE -ne 0) { throw "Example compilation failed." }
    $ClassPath = @("target/stage3-examples/classes", "formal-verification-core/target/classes") -join [IO.Path]::PathSeparator
    & $Python scripts/verify_contracts.py --jbmc $Jbmc --manifest target/stage3-examples/classes/META-INF/formal-verification/manifest.json `
        --classpath $ClassPath --output target/stage3-examples/results
    if ($LASTEXITCODE -ne 1) { throw "The negative examples must produce verification exit code 1." }
    $Report = Get-Content -Raw -Encoding UTF8 target/stage3-examples/results/report.json | ConvertFrom-Json
    $Expected = @{
        'DEMO-BROKEN-WITHDRAW' = 'COUNTEREXAMPLE'
        'DEMO-CONTRADICTORY-PRECONDITIONS' = 'NO_ADMISSIBLE_INPUTS'
        'DEMO-PRECONDITION-OUTSIDE-BOUNDS' = 'NO_ADMISSIBLE_INPUTS'
    }
    if ($Report.generated.Count -ne $Expected.Count) { throw "Unexpected number of results." }
    foreach ($Row in $Report.generated) {
        if (-not $Expected.ContainsKey($Row.id) -or $Expected[$Row.id] -ne $Row.status) { throw "Unexpected result for $($Row.id)." }
    }
    Write-Host "All three negative examples produced the expected results."
    $global:LASTEXITCODE = 0
}
finally { Pop-Location }
