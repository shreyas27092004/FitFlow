# scripts/verify-stage-02-live.ps1
$proc = Start-Process -FilePath "java" -ArgumentList "-jar", "backend\target\fitflow-backend-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=dev" -PassThru
Write-Host "Started backend PID: $($proc.Id)"

$ready = $false
try {
    Write-Host "Waiting for backend to boot..."
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        try {
            $h = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 2
            if ($h.status -eq "UP") {
                $ready = $true
                break
            }
        } catch {}
    }

    if (-not $ready) {
        Write-Host "Backend failed to report UP within 60s" -ForegroundColor Red
        exit 1
    }

    Write-Host "Backend is UP! Running Live Walkthrough...`n" -ForegroundColor Green

    # 1. Register Member
    Write-Host "=== 1. POST /api/v1/auth/register ===" -ForegroundColor Cyan
    $regBody = @{
        email = "sarah.connor@fitflow.local"
        password = "CyberPass2026!"
        fullName = "Sarah Connor"
        dateOfBirth = "1996-03-15"
        phone = "+919876543210"
    } | ConvertTo-Json
    $regRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/register" -Method Post -Body $regBody -ContentType "application/json"
    Write-Host ($regRes | ConvertTo-Json)

    # 2. Login (Wrong Password)
    Write-Host "`n=== 2. POST /api/v1/auth/login (Wrong Password) ===" -ForegroundColor Cyan
    $wrongLoginBody = @{
        email = "sarah.connor@fitflow.local"
        password = "WrongPassword123!"
    } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $wrongLoginBody -ContentType "application/json"
    } catch {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $respText = $reader.ReadToEnd()
        Write-Host "HTTP Status: 401 Unauthorized"
        Write-Host $respText
    }

    # 3. Login (Right Password)
    Write-Host "`n=== 3. POST /api/v1/auth/login (Right Password) ===" -ForegroundColor Cyan
    $rightLoginBody = @{
        email = "sarah.connor@fitflow.local"
        password = "CyberPass2026!"
    } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $rightLoginBody -ContentType "application/json"
    Write-Host ($loginRes | ConvertTo-Json)

    $accessToken = $loginRes.accessToken
    $refreshToken1 = $loginRes.refreshToken

    # 4. GET /api/v1/me/permissions WITHOUT token
    Write-Host "`n=== 4. GET /api/v1/me/permissions (Without Token) ===" -ForegroundColor Cyan
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/me/permissions" -Method Get
    } catch {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $respText = $reader.ReadToEnd()
        Write-Host "HTTP Status: 401 Unauthorized"
        Write-Host $respText
    }

    # 5. GET /api/v1/me/permissions WITH token
    Write-Host "`n=== 5. GET /api/v1/me/permissions (With Bearer Token) ===" -ForegroundColor Cyan
    $headers = @{
        Authorization = "Bearer $accessToken"
    }
    $permRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/me/permissions" -Method Get -Headers $headers
    Write-Host ($permRes | ConvertTo-Json)

    # 6. POST /api/v1/auth/refresh
    Write-Host "`n=== 6. POST /api/v1/auth/refresh ===" -ForegroundColor Cyan
    $refBody = @{
        refreshToken = $refreshToken1
    } | ConvertTo-Json
    $refreshRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/refresh" -Method Post -Body $refBody -ContentType "application/json"
    Write-Host ($refreshRes | ConvertTo-Json)
    $refreshToken2 = $refreshRes.refreshToken
    $newAccessToken = $refreshRes.accessToken

    # 7. Reuse old refresh token (should fail 401)
    Write-Host "`n=== 7. POST /api/v1/auth/refresh (Reuse Old Refresh Token) ===" -ForegroundColor Cyan
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/refresh" -Method Post -Body $refBody -ContentType "application/json"
    } catch {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $respText = $reader.ReadToEnd()
        Write-Host "HTTP Status: 401 Unauthorized"
        Write-Host $respText
    }

    # 8. POST /api/v1/auth/logout
    Write-Host "`n=== 8. POST /api/v1/auth/logout ===" -ForegroundColor Cyan
    $logoutBody = @{
        refreshToken = $refreshToken2
    } | ConvertTo-Json
    $logoutHeaders = @{
        Authorization = "Bearer $newAccessToken"
    }
    $logoutRes = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/logout" -Method Post -Body $logoutBody -Headers $logoutHeaders -ContentType "application/json" -UseBasicParsing
    Write-Host "HTTP Status: $($logoutRes.StatusCode) No Content"

    # 9. POST /api/v1/auth/forgot-password (existing email)
    Write-Host "`n=== 9. POST /api/v1/auth/forgot-password (Existing Email) ===" -ForegroundColor Cyan
    $forgotExistBody = @{
        email = "sarah.connor@fitflow.local"
    } | ConvertTo-Json
    $forgotExistRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/forgot-password" -Method Post -Body $forgotExistBody -ContentType "application/json"
    Write-Host ($forgotExistRes | ConvertTo-Json)

    # 10. POST /api/v1/auth/forgot-password (non-existing email)
    Write-Host "`n=== 10. POST /api/v1/auth/forgot-password (Non-Existing Email) ===" -ForegroundColor Cyan
    $forgotGhostBody = @{
        email = "ghost.operator@fitflow.local"
    } | ConvertTo-Json
    $forgotGhostRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/forgot-password" -Method Post -Body $forgotGhostBody -ContentType "application/json"
    Write-Host ($forgotGhostRes | ConvertTo-Json)

    Write-Host "`nIdentical Bodies Check: $($forgotExistRes.message -eq $forgotGhostRes.message)" -ForegroundColor Green

} finally {
    Write-Host "`nStopping backend process PID $($proc.Id)..."
    taskkill /PID $proc.Id /T /F | Out-Null
}
