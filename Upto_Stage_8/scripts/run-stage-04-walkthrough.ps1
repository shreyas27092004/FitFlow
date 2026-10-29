# ==============================================================================
# Helper to boot dev backend, run Stage 4 live walkthrough, and stop cleanly
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "Building latest FitFlow backend jar..." -ForegroundColor Yellow
& mvn -f backend/pom.xml package -DskipTests

Write-Host "Starting FitFlow backend in dev profile..." -ForegroundColor Yellow
$appProcess = Start-Process -FilePath "java" -ArgumentList "-jar", "backend\target\fitflow-backend-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=dev" -RedirectStandardOutput "backend\target\dev-boot.log" -RedirectStandardError "backend\target\dev-boot-err.log" -PassThru

try {
    $elapsed = 0
    $maxWait = 60
    $healthOk = $false

    while ($elapsed -lt $maxWait) {
        Start-Sleep -Seconds 3
        $elapsed += 3
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "DOWN")) {
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

    Write-Host "Backend is UP! Running Stage 4 live walkthrough..." -ForegroundColor Green
    & powershell -ExecutionPolicy Bypass -File "scripts\verify-stage-04-live.ps1"
} finally {
    Write-Host "Shutting down backend process (PID: $($appProcess.Id))..." -ForegroundColor Yellow
    Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue
}
