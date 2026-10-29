# ==============================================================================
# FitFlow Stage 4 Live Endpoint Walkthrough Script (PowerShell)
# ==============================================================================
param (
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

if (Test-Path "C:\Program Files\MySQL\MySQL Server 8.0\bin") {
    $env:PATH = "C:\Program Files\MySQL\MySQL Server 8.0\bin;" + $env:PATH
}

Write-Host "=== FitFlow Stage 4 Live Walkthrough ===" -ForegroundColor Cyan

# 1. Health check preflight
Write-Host "`n1. Checking application health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
Write-Host "Health: $($health.status)" -ForegroundColor Green

# Clean up any leftover walkthrough artifacts from prior runs
cmd.exe /c "mysql -u fitflow_app -pfitflow_pass fitflow -e `"DELETE FROM trainer_session_earnings; DELETE FROM attendance_records; DELETE FROM bookings; DELETE FROM waitlist_entries; DELETE FROM class_sessions WHERE room LIKE 'Studio Alpha%' OR room LIKE 'VIP Studio%';`" 2>nul"

# 2. Authenticate Admin and Trainer
Write-Host "`n2. Authenticating Admin and Trainer Alex..." -ForegroundColor Yellow
$adminLoginBody = @{
    email = "dev@fitflow.local"
    password = "FitFlowDev2026!"
} | ConvertTo-Json
$adminLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
$adminToken = $adminLoginRes.accessToken
$headersAdmin = @{ Authorization = "Bearer $adminToken" }
Write-Host "Admin authenticated (id=$($adminLoginRes.user.id))" -ForegroundColor Green

$alexLoginBody = @{
    email = "trainer.alex@fitflow.local"
    password = "FitFlowDev2026!"
} | ConvertTo-Json
$alexLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $alexLoginBody -ContentType "application/json"
$alexToken = $alexLoginRes.accessToken
$alexId = $alexLoginRes.user.id
$headersAlex = @{ Authorization = "Bearer $alexToken" }
Write-Host "Trainer Alex authenticated (id=$alexId)" -ForegroundColor Green

# 3. Configure Trainer Availability
Write-Host "`n3. Configuring Trainer Alex Availability (All week 06:00 - 22:00)..." -ForegroundColor Yellow
$alexProfile = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me" -Method Get -Headers $headersAlex
$availabilityList = @(
    @{ dayOfWeek = 1; start = "06:00"; end = "22:00" },
    @{ dayOfWeek = 2; start = "06:00"; end = "22:00" },
    @{ dayOfWeek = 3; start = "06:00"; end = "22:00" },
    @{ dayOfWeek = 4; start = "06:00"; end = "22:00" },
    @{ dayOfWeek = 5; start = "06:00"; end = "22:00" },
    @{ dayOfWeek = 6; start = "06:00"; end = "22:00" },
    @{ dayOfWeek = 7; start = "06:00"; end = "22:00" }
)
$updateProfileBody = @{
    availability = $availabilityList
    version = $alexProfile.version
} | ConvertTo-Json -Depth 5
$updatedAlexProfile = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me" -Method Patch -Headers $headersAlex -Body $updateProfileBody -ContentType "application/json"
Write-Host "Trainer Alex availability set successfully. Rate = $($updatedAlexProfile.ratePerSessionPaise) paise" -ForegroundColor Green

# 4. Admin creates a Fitness Class
Write-Host "`n4. Admin creates Fitness Class..." -ForegroundColor Yellow
$randomTag = Get-Random -Minimum 1000 -Maximum 9999
$createClassBody = @{
    name = "Live Stage 4 HIIT Flow $randomTag"
    description = "High intensity interval training with mobility recovery"
    category = "HIIT"
    durationMinutes = 60
    intensity = "HIGH"
    impactLevel = "HIGH"
    metValue = 8.5
    contraindicationTags = @("high_impact", "high_cardio")
    minAge = 16
    defaultCapacity = 20
} | ConvertTo-Json
$createdClass = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/classes" -Method Post -Headers $headersAdmin -Body $createClassBody -ContentType "application/json"
$classId = $createdClass.id
Write-Host "Class created: id=$classId, name='$($createdClass.name)', reviewed=$($createdClass.metadataReviewed)" -ForegroundColor Green

# 5. Admin creates Recurring Session series
Write-Host "`n5. Admin creates Recurring Sessions..." -ForegroundColor Yellow
$recurrenceStart = [System.DateTime]::UtcNow.AddDays(2).ToString("yyyy-MM-ddT14:00:00Z")
$recurrenceUntil = [System.DateTime]::UtcNow.AddDays(9).ToString("yyyy-MM-dd")
$createRecurringBody = @{
    classId = $classId
    trainerId = $alexId
    startsAt = $recurrenceStart
    room = "Studio Alpha"
    recurrence = @{
        daysOfWeek = @(1, 2, 3, 4, 5, 6, 7)
        until = $recurrenceUntil
    }
} | ConvertTo-Json -Depth 5
$recurringSessions = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/sessions" -Method Post -Headers $headersAdmin -Body $createRecurringBody -ContentType "application/json"
Write-Host "Created $($recurringSessions.Count) recurring sessions. Series ID: $($recurringSessions[0].seriesId)" -ForegroundColor Green

# 6. Admin creates a 1-Capacity Session for Waitlist Demo
Write-Host "`n6. Admin creates 1-Capacity Session for Waitlist & Promotion Demo..." -ForegroundColor Yellow
$sessionStart = [System.DateTime]::UtcNow.AddDays(3).ToString("yyyy-MM-ddT10:00:00Z")
$createSingleBody = @{
    classId = $classId
    trainerId = $alexId
    startsAt = $sessionStart
    capacity = 1
    room = "VIP Studio $randomTag"
} | ConvertTo-Json
$vipSessions = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/sessions" -Method Post -Headers $headersAdmin -Body $createSingleBody -ContentType "application/json"
$targetSession = $vipSessions[0]
$targetSessionId = $targetSession.id
Write-Host "Created single session: id=$targetSessionId, capacity=$($targetSession.capacity), room='$($targetSession.room)'" -ForegroundColor Green

# 7. Register 2 Members
Write-Host "`n7. Registering Member 1 and Member 2..." -ForegroundColor Yellow
$m1Email = "member1.s4.$randomTag@fitflow.local"
$m2Email = "member2.s4.$randomTag@fitflow.local"
$mPassword = "FitFlowMember2026!"

$reg1 = @{ email = $m1Email; password = $mPassword; fullName = "Member One"; dateOfBirth = "1998-01-01" } | ConvertTo-Json
$reg2 = @{ email = $m2Email; password = $mPassword; fullName = "Member Two"; dateOfBirth = "1997-02-02" } | ConvertTo-Json
Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $reg1 -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $reg2 -ContentType "application/json" | Out-Null

$login1Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = $m1Email; password = $mPassword } | ConvertTo-Json) -ContentType "application/json"
$login2Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = $m2Email; password = $mPassword } | ConvertTo-Json) -ContentType "application/json"
$m1Token = $login1Res.accessToken
$m2Token = $login2Res.accessToken
$m1Id = $login1Res.user.id
$m2Id = $login2Res.user.id
$headersM1 = @{ Authorization = "Bearer $m1Token"; "Idempotency-Key" = "key-live-m1-$randomTag" }
$headersM2 = @{ Authorization = "Bearer $m2Token"; "Idempotency-Key" = "key-live-m2-$randomTag" }
Write-Host "Registered and authenticated Member 1 (id=$m1Id) and Member 2 (id=$m2Id)" -ForegroundColor Green

# 8. Member 1 books the 1-capacity session -> 201 CONFIRMED
Write-Host "`n8. Member 1 books the 1-capacity session..." -ForegroundColor Yellow
$bookReq = @{ sessionId = $targetSessionId } | ConvertTo-Json
$booking1Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/bookings" -Method Post -Headers $headersM1 -Body $bookReq -ContentType "application/json"
$booking1Id = $booking1Res.bookingId
Write-Host "Member 1 Booking result: status=$($booking1Res.status), bookingId=$booking1Id, sessionId=$($booking1Res.sessionId)" -ForegroundColor Green

# 9. Member 2 attempts to book the full session -> 202 WAITLISTED position 1
Write-Host "`n9. Member 2 attempts to book full session (Expect HTTP 202 WAITLISTED)..." -ForegroundColor Yellow
$booking2Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/bookings" -Method Post -Headers $headersM2 -Body $bookReq -ContentType "application/json"
Write-Host "Member 2 Booking result: status=$($booking2Res.status), position=$($booking2Res.position)" -ForegroundColor Green

# 10. Member 1 cancels booking -> triggers waitlist promotion
Write-Host "`n10. Member 1 cancels booking (triggers Waitlist Promotion)..." -ForegroundColor Yellow
Invoke-RestMethod -Uri "$BaseUrl/api/v1/bookings/$booking1Id" -Method Delete -Headers @{ Authorization = "Bearer $m1Token" } | Out-Null
Write-Host "Member 1 cancelled booking $booking1Id successfully (HTTP 204)" -ForegroundColor Green

# 11. Verify Member 2 is now PROMOTED to CONFIRMED
Write-Host "`n11. Verifying Member 2 waitlist promotion..." -ForegroundColor Yellow
$m2Bookings = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/bookings" -Method Get -Headers @{ Authorization = "Bearer $m2Token" }
$m2Confirmed = $m2Bookings.items | Where-Object { $_.sessionId -eq $targetSessionId }
$m2BookingId = $m2Confirmed.bookingId
Write-Host "Member 2 booking in session $targetSessionId is now: status=$($m2Confirmed.status), bookingId=$m2BookingId" -ForegroundColor Green

# 12. Move session to past in MySQL so attendance & complete windows open
Write-Host "`n12. Moving session $targetSessionId to the past via MySQL CLI to open attendance window..." -ForegroundColor Yellow
$mysqlCmd = "mysql -u fitflow_app -pfitflow_pass fitflow -e `"UPDATE class_sessions SET starts_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 70 MINUTE), ends_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 10 MINUTE) WHERE id = $targetSessionId;`" 2>nul"
cmd.exe /c $mysqlCmd
Write-Host "Session $targetSessionId updated to past window" -ForegroundColor Green

# 13. Trainer Alex marks Member 2 attendance as ATTENDED
Write-Host "`n13. Trainer Alex marks attendance for Member 2..." -ForegroundColor Yellow
$attendanceBody = @{
    records = @(
        @{ bookingId = $m2BookingId; status = "ATTENDED" }
    )
} | ConvertTo-Json -Depth 5
$attendRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/sessions/$targetSessionId/attendance" -Method Post -Headers $headersAlex -Body $attendanceBody -ContentType "application/json"
Write-Host "Trainer marked attendance: sessionId=$($attendRes.sessionId), markedCount=$($attendRes.markedCount)" -ForegroundColor Green

# 14. Trainer Alex completes the session
Write-Host "`n14. Trainer Alex completes the session..." -ForegroundColor Yellow
$completeBody = @{ noAttendees = $false } | ConvertTo-Json
$completeRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/sessions/$targetSessionId/complete" -Method Post -Headers $headersAlex -Body $completeBody -ContentType "application/json"
Write-Host "Session completed: sessionId=$($completeRes.sessionId), status=$($completeRes.status), earningsPaise=$($completeRes.earningsPaise)" -ForegroundColor Green

# 15. Query MySQL CLI directly to verify trainer_session_earnings row
Write-Host "`n15. Verifying captured trainer_session_earnings row via direct MySQL CLI query..." -ForegroundColor Cyan
cmd.exe /c "mysql -u fitflow_app -pfitflow_pass fitflow -t -e `"SELECT id, session_id, trainer_id, amount_paise, status, created_at FROM trainer_session_earnings WHERE session_id = $targetSessionId;`" 2>nul"

Write-Host "`n=== FitFlow Stage 4 Live Walkthrough COMPLETED SUCCESSFULLY ===" -ForegroundColor Green
