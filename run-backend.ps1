$ErrorActionPreference = "Stop"

Write-Host "Starting event-only Spring Boot backend; run cv-pipeline\run_local_cv_workers.py separately for local GPU inference."

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) {
    & $maven.Source spring-boot:run
    exit $LASTEXITCODE
}

$downloadedMaven = "C:\Users\Swayam Dash\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd"
if (Test-Path -LiteralPath $downloadedMaven) {
    & $downloadedMaven spring-boot:run
    exit $LASTEXITCODE
}

throw "Maven was not found. Add mvn.cmd to PATH or install Maven 3.9+."
