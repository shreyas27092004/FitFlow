# FitFlow Live Agent Evaluation Suite Runner
param (
    [string]$ApiKey = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=== FitFlow Live Agent Evaluation Runner ===" -ForegroundColor Cyan

if ($ApiKey -ne "") {
    $env:GEMINI_API_KEY = $ApiKey
}

if (-not $env:GEMINI_API_KEY) {
    Write-Warning "GEMINI_API_KEY is not set. LiveAgentEvalIT will be skipped by assumption."
}

$env:FITFLOW_LIVE_EVAL = "1"

try {
    Write-Host "Running LiveAgentEvalIT against live Gemini model..." -ForegroundColor Yellow
    mvn -f backend/pom.xml test -Dtest=LiveAgentEvalIT
} finally {
    $env:FITFLOW_LIVE_EVAL = "0"
}

Write-Host "=== Live Evaluation Run Completed ===" -ForegroundColor Green
