$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$cvPython = Join-Path $projectRoot "cv-pipeline\venv\Scripts\python.exe"
$cvPipeline = Join-Path $projectRoot "cv-pipeline"

if (-not (Test-Path -LiteralPath $cvPython)) {
    throw "CV venv interpreter not found at $cvPython. Create cv-pipeline\venv first."
}

$env:NIRIKSHAN_PYTHON = $cvPython
$env:NIRIKSHAN_CV_PIPELINE_DIR = $cvPipeline
Write-Host "NIRIKSHAN_PYTHON=$env:NIRIKSHAN_PYTHON"
Write-Host "Starting backend with the CV venv..."

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
