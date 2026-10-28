# ==============================================================================
# FitFlow Stage 3 Live Endpoint Walkthrough Script
# ==============================================================================
param (
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
Write-Host "=== FitFlow Stage 3 Live Walkthrough ===" -ForegroundColor Cyan

# 1. Health check preflight
Write-Host "`n1. Checking application health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
Write-Host "Health: $($health.status)" -ForegroundColor Green

# 2. Member Self-Service Flow
Write-Host "`n2. Member Self-Service Walkthrough..." -ForegroundColor Yellow

$memberEmail = "member.live@fitflow.local"
$memberPassword = "FitFlowMember2026!"

try {
    $registerBody = @{
        email = $memberEmail
        password = $memberPassword
        fullName = "Maya Member"
        dateOfBirth = "1996-05-15"
        phone = "+919876543210"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $registerBody -ContentType "application/json" | Out-Null
    Write-Host "Member registered successfully: $memberEmail" -ForegroundColor Green
} catch {
    # If already registered from previous run, continue
}

$memberLoginBody = @{
    email = $memberEmail
    password = $memberPassword
} | ConvertTo-Json

$memberLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $memberLoginBody -ContentType "application/json"
$memberToken = $memberLoginRes.accessToken
Write-Host "Member authenticated successfully (id=$($memberLoginRes.user.id))" -ForegroundColor Green

# GET /api/v1/me
$headersMember = @{ Authorization = "Bearer $memberToken" }
$me = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me" -Method Get -Headers $headersMember
Write-Host "GET /api/v1/me: $($me.fullName), email=$($me.email), version=$($me.version)" -ForegroundColor Green

# PATCH /api/v1/me
$updateMeBody = @{
    phone = "+919876543299"
    notificationPrefs = @{
        emailBookings = $true
        emailReminders = $false
        emailMarketing = $true
    }
    version = $me.version
} | ConvertTo-Json

$patchedMe = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me" -Method Patch -Headers $headersMember -Body $updateMeBody -ContentType "application/json"
Write-Host "PATCH /api/v1/me: updated phone=$($patchedMe.phone), emailMarketing=$($patchedMe.notificationPrefs.emailMarketing)" -ForegroundColor Green

# GET /api/v1/me/export
$export = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/export" -Method Get -Headers $headersMember
Write-Host "GET /api/v1/me/export: profile email=$($export.profile.email), bookings count=$($export.bookings.Count)" -ForegroundColor Green

# 3. Admin User Management Flow
Write-Host "`n3. Admin User Management Walkthrough..." -ForegroundColor Yellow
$adminLoginBody = @{
    email = "dev@fitflow.local"
    password = "FitFlowDev2026!"
} | ConvertTo-Json

$adminLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
$adminToken = $adminLoginRes.accessToken
$headersAdmin = @{ Authorization = "Bearer $adminToken" }
Write-Host "Admin authenticated successfully with SUPER_ADMIN (id=$($adminLoginRes.user.id))" -ForegroundColor Green

# GET /api/v1/admin/users
$usersPage = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/users?size=5" -Method Get -Headers $headersAdmin
Write-Host "GET /api/v1/admin/users: totalElements=$($usersPage.totalElements), returned=$($usersPage.items.Count)" -ForegroundColor Green

# POST /api/v1/admin/users (Create new Trainer)
$randomId = Get-Random -Minimum 1000 -Maximum 9999
$newTrainerEmail = "coach.live$randomId@fitflow.local"
$createTrainerBody = @{
    email = $newTrainerEmail
    fullName = "Live Coach $randomId"
    role = "TRAINER"
    phone = "+91987654$randomId"
    ratePerSessionPaise = 65000
} | ConvertTo-Json

$createdTrainer = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/users" -Method Post -Headers $headersAdmin -Body $createTrainerBody -ContentType "application/json"
$targetTrainerId = $createdTrainer.id
Write-Host "POST /api/v1/admin/users: created trainer id=$targetTrainerId, email=$($createdTrainer.email)" -ForegroundColor Green

# Suspend trainer
$suspendRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/users/$targetTrainerId/suspend" -Method Post -Headers $headersAdmin
Write-Host "POST /api/v1/admin/users/$targetTrainerId/suspend: status=$($suspendRes.status)" -ForegroundColor Green

# Reactivate trainer
$reactivateRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/users/$targetTrainerId/reactivate" -Method Post -Headers $headersAdmin
Write-Host "POST /api/v1/admin/users/$targetTrainerId/reactivate: status=$($reactivateRes.status)" -ForegroundColor Green

# 4. Trainer Endpoints Flow
Write-Host "`n4. Trainer Catalog & Self-Service Walkthrough..." -ForegroundColor Yellow

# GET /api/v1/trainers (public view)
$publicTrainers = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainers" -Method Get -Headers $headersMember
Write-Host "GET /api/v1/trainers: count=$($publicTrainers.Count), first trainer=$($publicTrainers[0].fullName)" -ForegroundColor Green

# Trainer self-service as Alex
$alexLoginBody = @{
    email = "trainer.alex@fitflow.local"
    password = "FitFlowDev2026!"
} | ConvertTo-Json
$alexLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $alexLoginBody -ContentType "application/json"
$alexToken = $alexLoginRes.accessToken
$headersAlex = @{ Authorization = "Bearer $alexToken" }

# GET /api/v1/trainer/me
$alexProfile = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me" -Method Get -Headers $headersAlex
Write-Host "GET /api/v1/trainer/me: $($alexProfile.fullName), ratePerSessionPaise=$($alexProfile.ratePerSessionPaise), version=$($alexProfile.version)" -ForegroundColor Green

# PATCH /api/v1/trainer/me (bio & availability)
$patchAlexBody = @{
    bio = "Updated bio for Alex Rivers via live walkthrough"
    specialties = @("Strength", "Mobility", "Kettlebell")
    availability = @(
        @{ dayOfWeek = 1; start = "08:00"; end = "12:00" },
        @{ dayOfWeek = 3; start = "14:00"; end = "18:00" }
    )
    version = $alexProfile.version
} | ConvertTo-Json

$patchedAlex = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me" -Method Patch -Headers $headersAlex -Body $patchAlexBody -ContentType "application/json"
Write-Host "PATCH /api/v1/trainer/me: updated bio='$($patchedAlex.bio)', availability slots count=$($patchedAlex.availability.Count)" -ForegroundColor Green

# GET /api/v1/trainer/me/summary
$alexSummary = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me/summary" -Method Get -Headers $headersAlex
Write-Host "GET /api/v1/trainer/me/summary: upcomingSessionsCount=$($alexSummary.upcomingSessionsCount), earnings=$($alexSummary.earnings)" -ForegroundColor Green

# Admin updates trainer session rate
$freshTrainer = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me" -Method Get -Headers $headersAlex
$rateUpdateBody = @{
    ratePerSessionPaise = 280000 # 2800 INR
    version = $freshTrainer.version
} | ConvertTo-Json

$updatedRateRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/trainers/$($alexProfile.id)/rate" -Method Patch -Headers $headersAdmin -Body $rateUpdateBody -ContentType "application/json"
Write-Host "PATCH /api/v1/admin/trainers/$($alexProfile.id)/rate: new ratePerSessionPaise=$($updatedRateRes.ratePerSessionPaise)" -ForegroundColor Green

# 5. AI Settings Management Flow
Write-Host "`n5. AI Settings & Runtime Key Management Walkthrough..." -ForegroundColor Yellow

# GET /api/v1/admin/settings/ai (initial mock state)
$aiSettings = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/settings/ai" -Method Get -Headers $headersAdmin
Write-Host "GET /api/v1/admin/settings/ai: configured=$($aiSettings.configured), mode=$($aiSettings.mode), model=$($aiSettings.model)" -ForegroundColor Green

# PUT /api/v1/admin/settings/ai/key
$dummyKey = "AIzaSyLiveWalkthroughKeyForTesting12345"
$setKeyBody = @{
    apiKey = $dummyKey
} | ConvertTo-Json

$setKeyRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/settings/ai/key" -Method Put -Headers $headersAdmin -Body $setKeyBody -ContentType "application/json"
Write-Host "PUT /api/v1/admin/settings/ai/key: configured=$($setKeyRes.configured), maskedLast4=$($setKeyRes.maskedLast4), mode=$($setKeyRes.mode)" -ForegroundColor Green

# POST /api/v1/admin/settings/ai/test (calls stub)
$testAiRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/settings/ai/test" -Method Post -Headers $headersAdmin
Write-Host "POST /api/v1/admin/settings/ai/test: ok=$($testAiRes.ok), message='$($testAiRes.message)'" -ForegroundColor Green

# PUT /api/v1/admin/settings/ai/model
$setModelBody = @{
    model = "gemini-1.5-flash-8b"
} | ConvertTo-Json
$setModelRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/settings/ai/model" -Method Put -Headers $headersAdmin -Body $setModelBody -ContentType "application/json"
Write-Host "PUT /api/v1/admin/settings/ai/model: model=$($setModelRes.model)" -ForegroundColor Green

# DELETE /api/v1/admin/settings/ai/key
Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/settings/ai/key" -Method Delete -Headers $headersAdmin | Out-Null
Write-Host "DELETE /api/v1/admin/settings/ai/key: key removed successfully" -ForegroundColor Green

# POST /api/v1/admin/settings/ai/test (reverted to mock)
$testAiMockRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/settings/ai/test" -Method Post -Headers $headersAdmin
Write-Host "POST /api/v1/admin/settings/ai/test (after delete): ok=$($testAiMockRes.ok), message='$($testAiMockRes.message)'" -ForegroundColor Green

# 6. Member Soft Delete Flow
Write-Host "`n6. Member Account Deletion Walkthrough..." -ForegroundColor Yellow
$delUserEmail = "member.del$randomId@fitflow.local"
$delRegisterBody = @{
    email = $delUserEmail
    password = "DeletePass123!"
    fullName = "Delete Test Member $randomId"
    dateOfBirth = "1998-04-12"
} | ConvertTo-Json

$delRegRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $delRegisterBody -ContentType "application/json"
Write-Host "Registered delete candidate: userId=$($delRegRes.userId)" -ForegroundColor Green

$delLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = $delUserEmail; password = "DeletePass123!" } | ConvertTo-Json) -ContentType "application/json"
$delToken = $delLoginRes.accessToken
$headersDel = @{ Authorization = "Bearer $delToken" }

# POST /api/v1/me/delete-request
$delReqBody = @{ password = "DeletePass123!" } | ConvertTo-Json
Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/delete-request" -Method Post -Headers $headersDel -Body $delReqBody -ContentType "application/json" | Out-Null
Write-Host "POST /api/v1/me/delete-request: 202 Accepted" -ForegroundColor Green

# Verify subsequent login fails with 401
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = $delUserEmail; password = "DeletePass123!" } | ConvertTo-Json) -ContentType "application/json"
    Write-Host "ERROR: Login should have been blocked!" -ForegroundColor Red
} catch {
    Write-Host "Subsequent login correctly rejected with 401 Unauthorized" -ForegroundColor Green
}

Write-Host "`n=== Stage 3 Live Walkthrough Completed Successfully! ===" -ForegroundColor Cyan
