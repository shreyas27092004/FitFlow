# ==============================================================================
# Export OpenAPI Specification (PowerShell)
# ==============================================================================
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$rootDir = Split-Path -Parent $PSScriptRoot
$docsApiDir = Join-Path $rootDir "docs\api"
if (-not (Test-Path $docsApiDir)) {
    New-Item -ItemType Directory -Path $docsApiDir -Force | Out-Null
}

$outputFile = Join-Path $docsApiDir "openapi.json"
$backendDir = Join-Path $rootDir "backend"
$jarPath = Join-Path $backendDir "target\fitflow-backend-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "Jar not found. Building backend..." -ForegroundColor Yellow
    Push-Location $backendDir
    try {
        & mvn package -DskipTests
    } finally {
        Pop-Location
    }
}

$appStartedHere = $false
$appProcess = $null

$isOpen = Test-NetConnection -ComputerName 127.0.0.1 -Port 8080 -InformationLevel Quiet
if (-not $isOpen) {
    Write-Host "Starting FitFlow backend in dev profile..." -ForegroundColor Cyan
    $stdoutPath = Join-Path $backendDir "target\openapi-export.log"
    $stderrPath = Join-Path $backendDir "target\openapi-export-err.log"
    $appProcess = Start-Process -FilePath "java" -ArgumentList "-jar", $jarPath, "--spring.profiles.active=dev" -WorkingDirectory $backendDir -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
    $appStartedHere = $true

    $ready = $false
    $elapsed = 0
    while (-not $ready -and $elapsed -lt 90) {
        Start-Sleep -Seconds 2
        $elapsed += 2
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:8080/v3/api-docs" -UseBasicParsing -TimeoutSec 3
            if ($resp.StatusCode -eq 200) {
                $ready = $true
            }
        } catch {}
    }

    if (-not $ready) {
        if ($appProcess -and -not $appProcess.HasExited) {
            Stop-Process -Id $appProcess.Id -Force
        }
        throw "Backend failed to start or /v3/api-docs did not become available within 90s."
    }
}

try {
    Write-Host "Fetching OpenAPI documentation from http://localhost:8080/v3/api-docs..." -ForegroundColor Cyan
    $rawJson = (Invoke-WebRequest -Uri "http://localhost:8080/v3/api-docs" -UseBasicParsing).Content

    # Format JSON with sorted keys using Node
    $tempRaw = Join-Path $docsApiDir "temp_raw_openapi.json"
    [System.IO.File]::WriteAllText($tempRaw, $rawJson, [System.Text.Encoding]::UTF8)

    $nodeScript = @"
const fs = require('fs');
function sortObject(obj) {
  if (obj === null || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(sortObject);
  const sorted = {};
  Object.keys(obj).sort().forEach(k => {
    sorted[k] = sortObject(obj[k]);
  });
  return sorted;
}
const raw = JSON.parse(fs.readFileSync(process.argv[2], 'utf8').replace(/^\uFEFF/, ''));
const sorted = sortObject(raw);
fs.writeFileSync(process.argv[3], JSON.stringify(sorted, null, 2) + '\n', 'utf8');
"@

    $tempScript = Join-Path $docsApiDir "sort_keys.js"
    [System.IO.File]::WriteAllText($tempScript, $nodeScript, [System.Text.Encoding]::UTF8)

    node $tempScript $tempRaw $outputFile

    Remove-Item $tempRaw -Force -ErrorAction SilentlyContinue
    Remove-Item $tempScript -Force -ErrorAction SilentlyContinue

    Write-Host "OpenAPI specification saved to $outputFile (sorted keys, formatted)." -ForegroundColor Green
} finally {
    if ($appStartedHere -and $appProcess -and -not $appProcess.HasExited) {
        Write-Host "Stopping temporary backend process (PID $($appProcess.Id))..." -ForegroundColor Yellow
        Stop-Process -Id $appProcess.Id -Force
        Wait-Process -Id $appProcess.Id -Timeout 10 -ErrorAction SilentlyContinue
    }
}
