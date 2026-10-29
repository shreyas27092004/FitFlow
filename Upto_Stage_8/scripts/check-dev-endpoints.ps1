# scripts/check-dev-endpoints.ps1
$proc = Start-Process -FilePath 'java' -ArgumentList '-jar', 'backend\target\fitflow-backend-0.0.1-SNAPSHOT.jar', '--spring.profiles.active=dev' -PassThru
Write-Host "Started backend PID: $($proc.Id)"
$ready = $false
try {
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -Method Get -TimeoutSec 2
            if ($health.status -eq 'UP') {
                $ready = $true
                break
            }
        } catch {}
    }

    if ($ready) {
        Write-Host "=== HEALTH CHECK ==="
        $healthJson = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' | ConvertTo-Json -Depth 6
        Write-Host $healthJson

        Write-Host "`n=== DEV MAIL CHECK ==="
        $mailRaw = Invoke-RestMethod -Uri 'http://localhost:8080/dev/mail' | ConvertTo-Json
        Write-Host $mailRaw

        Write-Host "`n=== SWAGGER UI CHECK ==="
        $swaggerRaw = Invoke-WebRequest -Uri 'http://localhost:8080/swagger-ui/index.html' -MaximumRedirection 3 -UseBasicParsing
        Write-Host "Swagger StatusCode: $($swaggerRaw.StatusCode)"
        Write-Host "Swagger Content Preview:"
        Write-Host $swaggerRaw.Content.Substring(0, [System.Math]::Min(300, $swaggerRaw.Content.Length))
    } else {
        Write-Host "Backend failed to report UP within 60s"
    }
} finally {
    Write-Host "`nStopping process..."
    taskkill /PID $proc.Id /T /F | Out-Null
}
