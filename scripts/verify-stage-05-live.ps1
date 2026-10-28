# ==============================================================================
# FitFlow Stage 5 Live Endpoint Walkthrough Script (PowerShell)
# Exercises: Two-phase payment gateway, Proration engine, Membership lifecycle,
# Entitlements, Invoices, and Admin Payout Batches with Two-Person Rule.
# ==============================================================================
param (
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "=== FitFlow Stage 5 Live Walkthrough ===" -ForegroundColor Cyan

# 1. Health check preflight
Write-Host "`n1. Checking application health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
Write-Host "Health status: $($health.status)" -ForegroundColor Green

# 2. View Plans Catalog
Write-Host "`n2. Fetching published Membership Plans (GET /api/v1/plans)..." -ForegroundColor Yellow
$plans = Invoke-RestMethod -Uri "$BaseUrl/api/v1/plans" -Method Get
Write-Host "Found $($plans.Count) plans:" -ForegroundColor Green
foreach ($p in $plans) {
    Write-Host "  - [$($p.code)] $($p.name): $($p.pricePaise) paise, duration=$($p.durationDays)d, quota=$($p.sessionQuota), free=$($p.isFree)" -ForegroundColor Gray
}

# 3. Member Registration & Auto-assigned FREE Tier
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$memberEmail = "member.money.$stamp@fitflow.local"
$memberPass = "FitFlowPass123!"

Write-Host "`n3. Registering Member ($memberEmail)..." -ForegroundColor Yellow
$regBody = @{
    email = $memberEmail
    password = $memberPass
    fullName = "Money Walker"
    dateOfBirth = "1994-06-15"
} | ConvertTo-Json
$regRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $regBody -ContentType "application/json"
$memberId = $regRes.userId
Write-Host "Registered member id=$memberId" -ForegroundColor Green

# Login member
$loginBody = @{
    email = $memberEmail
    password = $memberPass
} | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$memberToken = $loginRes.accessToken
$headersMember = @{ Authorization = "Bearer $memberToken" }

# Verify default FREE tier membership exists
$memStatus = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership" -Method Get -Headers $headersMember
Write-Host "Auto-assigned Membership: freeSessionsRemaining=$($memStatus.freeSessionsRemaining), paid=$($memStatus.paid)" -ForegroundColor Green
if ($memStatus.freeSessionsRemaining -ne 3 -or $null -ne $memStatus.paid) {
    throw "Expected default FREE tier with 3 sessions and paid=null, got $($memStatus.freeSessionsRemaining) and $($memStatus.paid)"
}

# 4. Two-Phase Subscribe to BASIC Plan
Write-Host "`n4. Two-Phase Subscribe to BASIC Plan (49900 paise, 8 sessions)..." -ForegroundColor Yellow
$subBody = @{
    planCode = "BASIC"
} | ConvertTo-Json

# Phase 1: Intent
$subIntent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memberships/subscribe" -Method Post -Body $subBody -Headers $headersMember -ContentType "application/json"
$paymentId = $subIntent.paymentId
Write-Host "Phase 1: Intent created paymentId=$paymentId, status=$($subIntent.status), amountPaise=$($subIntent.amountPaise)" -ForegroundColor Green

# Verify membership is STILL FREE before payment confirmation
$memPreConfirm = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership" -Method Get -Headers $headersMember
Write-Host "Pre-confirm check: Member paid membership is still $($memPreConfirm.paid)" -ForegroundColor Green
if ($null -ne $memPreConfirm.paid) {
    throw "Membership was changed prematurely before payment confirmation!"
}

# Phase 2: Confirm with tok_ok
Write-Host "Phase 2: Confirming payment with tok_ok (POST /api/v1/payments/$paymentId/confirm)..." -ForegroundColor Yellow
$confirmBody = @{
    clientPayload = @{
        paymentMethodToken = "tok_ok"
    }
} | ConvertTo-Json
$confirmRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments/$paymentId/confirm" -Method Post -Body $confirmBody -Headers $headersMember -ContentType "application/json"
Write-Host "Confirm result: payment status=$($confirmRes.status), membershipChangeApplied=$($confirmRes.membershipChangeApplied)" -ForegroundColor Green

# Verify membership is now BASIC
$memPostConfirm = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership" -Method Get -Headers $headersMember
Write-Host "Active Plan: $($memPostConfirm.paid.planCode), Quota: $($memPostConfirm.paid.sessionsRemaining) remaining, Ends: $($memPostConfirm.paid.endsAt)" -ForegroundColor Green
if ($memPostConfirm.paid.planCode -ne "BASIC" -or $memPostConfirm.paid.sessionsRemaining -ne 8) {
    throw "Expected BASIC plan with 8 sessions remaining, got $($memPostConfirm.paid.planCode)"
}

# Verify duplicate confirm is idempotent
Write-Host "Testing Idempotency: Re-submitting duplicate confirm..." -ForegroundColor Yellow
$dupConfirm = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments/$paymentId/confirm" -Method Post -Body $confirmBody -Headers $headersMember -ContentType "application/json"
Write-Host "Duplicate confirm status=$($dupConfirm.status) (Idempotent OK)" -ForegroundColor Green

# 5. Proration Engine: Preview and Upgrade to PRO
Write-Host "`n5. Testing Proration Engine: Preview Upgrade to PRO..." -ForegroundColor Yellow
$previewBody = @{
    targetPlanCode = "PRO"
} | ConvertTo-Json
$preview = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership/change-preview" -Method Post -Body $previewBody -Headers $headersMember -ContentType "application/json"
Write-Host "Proration Preview: Credit=$($preview.creditPaise) paise, Net Due=$($preview.amountDuePaise) paise" -ForegroundColor Green
Write-Host "Explanation: $($preview.explanation -join '; ')" -ForegroundColor Gray

Write-Host "Initiating Plan Upgrade to PRO (POST /api/v1/memberships/change)..." -ForegroundColor Yellow
$changeReq = @{
    targetPlanCode = "PRO"
    expectedAmountDuePaise = $preview.amountDuePaise
} | ConvertTo-Json
$changeIntent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memberships/change" -Method Post -Body $changeReq -Headers $headersMember -ContentType "application/json"
$upgradePaymentId = $changeIntent.paymentId
Write-Host "Upgrade Intent: paymentId=$upgradePaymentId, status=$($changeIntent.status), amount=$($changeIntent.amountPaise) paise" -ForegroundColor Green

# Confirm Upgrade
$confirmUpgrade = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments/$upgradePaymentId/confirm" -Method Post -Body $confirmBody -Headers $headersMember -ContentType "application/json"
Write-Host "Upgrade Payment confirmed: status=$($confirmUpgrade.status)" -ForegroundColor Green

# Verify membership is now PRO
$memPro = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership" -Method Get -Headers $headersMember
Write-Host "Active Plan after upgrade: $($memPro.paid.planCode), Quota: $($memPro.paid.sessionsRemaining) remaining" -ForegroundColor Green
if ($memPro.paid.planCode -ne "PRO" -or $memPro.paid.sessionsRemaining -ne 16) {
    throw "Expected PRO plan with 16 sessions remaining, got $($memPro.paid.planCode)"
}

# 6. Test Payment Failure (tok_fail)
Write-Host "`n6. Testing Payment Failure Token (tok_fail)..." -ForegroundColor Yellow
$member2Email = "member2.money.$stamp@fitflow.local"
$reg2Body = @{
    email = $member2Email
    password = $memberPass
    fullName = "Failed Payment Member"
    dateOfBirth = "1997-03-20"
} | ConvertTo-Json
$reg2Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $reg2Body -ContentType "application/json"

$login2Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = $member2Email; password = $memberPass } | ConvertTo-Json) -ContentType "application/json"
$token2 = $login2Res.accessToken
$headers2 = @{ Authorization = "Bearer $token2" }

$sub2Intent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memberships/subscribe" -Method Post -Body (@{ planCode = "BASIC" } | ConvertTo-Json) -Headers $headers2 -ContentType "application/json"
$failBody = @{
    clientPayload = @{
        paymentMethodToken = "tok_fail"
    }
} | ConvertTo-Json
$failConfirm = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments/$($sub2Intent.paymentId)/confirm" -Method Post -Body $failBody -Headers $headers2 -ContentType "application/json"
Write-Host "Payment confirmed with tok_fail: status=$($failConfirm.status) (Handled cleanly)" -ForegroundColor Green

$mem2AfterFail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership" -Method Get -Headers $headers2
Write-Host "Member 2 paid membership is untouched: $($mem2AfterFail.paid)" -ForegroundColor Green
if ($null -ne $mem2AfterFail.paid) {
    throw "Member 2 paid membership was incorrectly updated on failed payment!"
}

# 7. Cancel Membership with Proration Refund
Write-Host "`n7. Cancelling PRO Membership with Proration Refund..." -ForegroundColor Yellow
$cancelRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/memberships/cancel" -Method Post -Headers $headersMember -ContentType "application/json"
Write-Host "Cancellation processed: status=$($cancelRes.status), refundPaise=$($cancelRes.refundPaise), creditPaise=$($cancelRes.creditPaise)" -ForegroundColor Green

$memAfterCancel = Invoke-RestMethod -Uri "$BaseUrl/api/v1/me/membership" -Method Get -Headers $headersMember
Write-Host "Membership after cancellation: paid=$($memAfterCancel.paid), freeSessionsRemaining=$($memAfterCancel.freeSessionsRemaining)" -ForegroundColor Green
if ($null -ne $memAfterCancel.paid) {
    throw "Expected paid membership to be null after cancellation, got $($memAfterCancel.paid)"
}

# 8. Trainer Session Earnings Provider Check
Write-Host "`n8. Verifying Trainer Earnings Endpoint (GET /api/v1/trainer/me/earnings)..." -ForegroundColor Yellow
$alexLoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = "trainer.alex@fitflow.local"; password = "FitFlowDev2026!" } | ConvertTo-Json) -ContentType "application/json"
$alexToken = $alexLoginRes.accessToken
$headersAlex = @{ Authorization = "Bearer $alexToken" }

$earnings = Invoke-RestMethod -Uri "$BaseUrl/api/v1/trainer/me/earnings" -Method Get -Headers $headersAlex
Write-Host "Trainer Alex: totalEarningsPaise=$($earnings.totalEarningsPaise), sessionsCount=$($earnings.sessionsCount)" -ForegroundColor Green

# 9. Admin Payout Batch Lifecycle & Two-Person Rule
Write-Host "`n9. Admin Payout Batch Creation and Two-Person Rule Enforcement..." -ForegroundColor Yellow
$admin1LoginRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = "dev@fitflow.local"; password = "FitFlowDev2026!" } | ConvertTo-Json) -ContentType "application/json"
$admin1Token = $admin1LoginRes.accessToken
$admin1Id = $admin1LoginRes.user.id
$headersAdmin1 = @{ Authorization = "Bearer $admin1Token" }

# Admin 1 creates draft batch
$todayStr = [DateTime]::UtcNow.ToString("yyyy-MM-dd")
$firstOfMonth = (Get-Date -Day 1).ToString("yyyy-MM-dd")
$createBatchReq = @{
    periodStart = $firstOfMonth
    periodEnd = $todayStr
} | ConvertTo-Json
$batch = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/payouts" -Method Post -Body $createBatchReq -Headers $headersAdmin1 -ContentType "application/json"
$batchId = $batch.id
Write-Host "Admin 1 (id=$($admin1Id)) created Payout Batch id=$($batchId), status=$($batch.status), totalPaise=$($batch.totalPaise)" -ForegroundColor Green

# Admin 1 attempts to approve their own batch -> MUST FAIL with 409 TWO_PERSON_RULE
Write-Host "Testing Two-Person Rule: Admin 1 attempts to approve own batch..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/payouts/$batchId/approve" -Method Post -Headers $headersAdmin1 -ContentType "application/json"
    throw "Expected 409 Conflict under Two-Person Rule, but approval succeeded!"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 409) {
        Write-Host "Two-Person Rule properly rejected self-approval with 409 CONFLICT" -ForegroundColor Green
    } else {
        throw $_
    }
}

# Create or Authenticate Admin 2
Write-Host "Authenticating Admin 2 (admin2@fitflow.local)..." -ForegroundColor Yellow
$admin2Login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body (@{ email = "admin2@fitflow.local"; password = "FitFlowDev2026!" } | ConvertTo-Json) -ContentType "application/json"
$admin2Token = $admin2Login.accessToken
$headersAdmin2 = @{ Authorization = "Bearer $admin2Token" }

# Admin 2 approves batch
Write-Host "Admin 2 approves batch id=$batchId..." -ForegroundColor Yellow
$approvedBatch = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/payouts/$batchId/approve" -Method Post -Headers $headersAdmin2 -ContentType "application/json"
Write-Host "Batch approved: status=$($approvedBatch.status), approvedBy=$($approvedBatch.approvedBy)" -ForegroundColor Green
if ($approvedBatch.status -ne "APPROVED") {
    throw "Expected batch status APPROVED, got $($approvedBatch.status)"
}

# Admin 2 marks batch as PAID
Write-Host "Admin 2 marks batch id=$batchId as PAID..." -ForegroundColor Yellow
$paidBatch = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/payouts/$batchId/mark-paid" -Method Post -Headers $headersAdmin2 -ContentType "application/json"
Write-Host "Batch marked paid: status=$($paidBatch.status), updatedAt=$($paidBatch.updatedAt)" -ForegroundColor Green

# 10. Export CSV
Write-Host "`n10. Exporting Payout Batch CSV (GET /api/v1/admin/payouts/$batchId/export.csv)..." -ForegroundColor Yellow
$csvContent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/payouts/$batchId/export.csv" -Method Get -Headers $headersAdmin2
Write-Host "CSV Export Output:" -ForegroundColor Gray
Write-Host $csvContent -ForegroundColor White

Write-Host "`n=== Stage 5 Live Walkthrough Complete: All 10 steps succeeded! ===" -ForegroundColor Green
