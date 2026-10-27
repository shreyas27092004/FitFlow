# ==============================================================================
# Helper to boot dev backend, run Stage 3 live walkthrough, and stop cleanly
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "Starting FitFlow backend in dev profile..." -ForegroundColor Yellow
$appProcess = Start-Process -FilePath "java" -ArgumentList "-jar", "backend\target\fitflow-backend-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=dev" -PassThru

try {
    $elapsed = 0
    $maxWait = 60
    $healthOk = $false

    while ($elapsed -lt $maxWait) {
        Start-Sleep -Seconds 3
        $elapsed += 3
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp -and $resp.status -eq "UP") {
                $healthOk = $true
                break
            }
        } catch {
            Write-Host "Waiting for backend to boot ($elapsed / $maxWait s)..."
        }
    }

    if (-not $healthOk) {
        throw "Backend failed to become UP on http://localhost:8080 within $maxWait seconds"
    }

    Write-Host "Backend is UP! Running Stage 3 live walkthrough..." -ForegroundColor Green
    & powershell -ExecutionPolicy Bypass -File "scripts\verify-stage-03-live.ps1"
} finally {
    Write-Host "Shutting down backend process (PID: $($appProcess.Id))..." -ForegroundColor Yellow
    Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue
}
