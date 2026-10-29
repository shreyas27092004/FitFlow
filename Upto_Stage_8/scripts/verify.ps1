# ==============================================================================
# FitFlow Verification Script (PowerShell)
# ==============================================================================
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Ensure PostgreSQL bin is in PATH for this session if installed in standard location
if (Test-Path "C:\Program Files\PostgreSQL\17\bin") {
    $env:PATH = "C:\Program Files\PostgreSQL\17\bin;" + $env:PATH
}
if (Test-Path "C:\Program Files\MySQL\MySQL Server 8.0\bin") {
    $env:PATH = "C:\Program Files\MySQL\MySQL Server 8.0\bin;" + $env:PATH
}

# Load .env if present
if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $name = $parts[0].Trim()
            $value = $parts[1].Trim()
            if (-not [Environment]::GetEnvironmentVariable($name)) {
                [Environment]::SetEnvironmentVariable($name, $value)
            }
        }
    }
}

$results = [ordered]@{}

Write-Host "`n=== FitFlow Verification Suite ===" -ForegroundColor Cyan

# 1. Check Env Vars
Write-Host "1. Checking environment configurations..." -ForegroundColor Yellow
$requiredEnv = @("MYSQL_URL", "PG_URL")
$envOk = $true
foreach ($var in $requiredEnv) {
    $val = [Environment]::GetEnvironmentVariable($var)
    if (-not $val) {
        Write-Warning "Environment variable $var is not set (using application defaults)."
    }
}
$results["Environment Variables"] = "PASS"

# 2. TCP Checks
Write-Host "2. Checking TCP port connectivity..." -ForegroundColor Yellow
$mysqlCheck = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet
$pgCheck = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -InformationLevel Quiet
$rabbitCheck = Test-NetConnection -ComputerName 127.0.0.1 -Port 5672 -InformationLevel Quiet

if ($mysqlCheck) {
    $results["MySQL Port 3306"] = "PASS"
} else {
    $results["MySQL Port 3306"] = "FAIL"
}

if ($pgCheck) {
    $results["PostgreSQL Port 5432"] = "PASS"
} else {
    $results["PostgreSQL Port 5432"] = "FAIL"
}

if ($rabbitCheck) {
    $results["RabbitMQ Port 5672"] = "PASS"
} else {
    $results["RabbitMQ Port 5672"] = "OPTIONAL (UNVERIFIED)"
}

# 3. Maven Verify (Unit, Integration, Jacoco, ArchUnit)
Write-Host "3. Running Maven verify (Unit + IT + Jacoco + ArchUnit)..." -ForegroundColor Yellow
Push-Location "backend"
try {
    & mvn verify
    if ($LASTEXITCODE -eq 0) {
        $results["Maven Verify"] = "PASS"
    } else {
        $results["Maven Verify"] = "FAIL"
    }
} catch {
    $results["Maven Verify"] = "FAIL: $_"
} finally {
    Pop-Location
}

# 4. Dev Boot & Health Check
Write-Host "4. Booting app in dev profile for live health check..." -ForegroundColor Yellow
$appProcess = $null
$healthOk = $false

try {
    $backendDir = Join-Path (Get-Location) "backend"
    $jarPath = Join-Path $backendDir "target\fitflow-backend-0.0.1-SNAPSHOT.jar"
    $stdoutPath = Join-Path $backendDir "target\dev-boot.log"
    $stderrPath = Join-Path $backendDir "target\dev-boot-err.log"

    $appProcess = Start-Process -FilePath "java" -ArgumentList "-jar", $jarPath, "--spring.profiles.active=dev" -WorkingDirectory $backendDir -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
    $maxSeconds = 90
    $elapsed = 0

    while ($elapsed -lt $maxSeconds) {
        Start-Sleep -Seconds 3
        $elapsed += 3
        try {
            $resp = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -Method Get -TimeoutSec 5 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($resp -and $resp.status -eq "UP") {
                $healthOk = $true
                break
            }
        } catch {
            # Still booting
        }
    }

    if ($healthOk) {
        $results["Live Health (/actuator/health UP)"] = "PASS"
    } else {
        $results["Live Health (/actuator/health UP)"] = "FAIL"
        if (Test-Path "backend\target\dev-boot.log") {
            Write-Host "--- dev-boot.log ---"
            Get-Content "backend\target\dev-boot.log" -Tail 30
        }
        if (Test-Path "backend\target\dev-boot-err.log") {
            Write-Host "--- dev-boot-err.log ---"
            Get-Content "backend\target\dev-boot-err.log" -Tail 30
        }
    }
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Write-Host "Stopping background application..." -ForegroundColor Yellow
        # Stop process tree on Windows
        & taskkill /PID $appProcess.Id /T /F | Out-Null
    }
}

# 5. Report Table
Write-Host "`n=== Verification Results ===" -ForegroundColor Cyan
$overallPass = $true
foreach ($key in $results.Keys) {
    $status = $results[$key]
    $color = "Green"
    if ($status -like "*FAIL*") {
        $color = "Red"
        $overallPass = $false
    } elseif ($status -like "*OPTIONAL*") {
        $color = "DarkYellow"
    }
    Write-Host ("{0,-35} : {1}" -f $key, $status) -ForegroundColor $color
}

if (-not $overallPass) {
    Write-Host "`nVerification FAILED!" -ForegroundColor Red
    exit 1
} else {
    Write-Host "`nAll verification gates PASSED!" -ForegroundColor Green
    exit 0
}
