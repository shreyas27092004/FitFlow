# FitFlow Stage 2: Authentication, Users & RBAC Evidence Report

**Generated Date**: 2026-10-28  
**Project**: FitFlow (GenAI Fitness Class Monolith)  
**Stage**: Stage 2 of 13  
**Status**: COMPLETE (All gates passed)

---

## 1. Automated Verification Suite (`scripts/verify.ps1`)

```text
=== FitFlow Verification Suite ===
1. Checking environment configurations...
WARNING: Environment variable MYSQL_URL is not set (using application defaults).
WARNING: Environment variable PG_URL is not set (using application defaults).
2. Checking TCP port connectivity...
3. Running Maven verify (Unit + IT + Jacoco + ArchUnit)...

[INFO] Scanning for projects...
[INFO] --------------------< com.fitflow:fitflow-backend >---------------------
[INFO] Building fitflow-backend 0.0.1-SNAPSHOT
[INFO] 
[INFO] --- enforcer:3.5.0:enforce (enforce-versions) @ fitflow-backend ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.BanDuplicatePomDependencyVersions passed
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ fitflow-backend ---
[INFO] Running com.fitflow.admin.AdminActionLoggerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.236 s -- in com.fitflow.admin.AdminActionLoggerTest
[INFO] Running com.fitflow.ArchitectureRulesTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.243 s -- in com.fitflow.ArchitectureRulesTest
[INFO] Running com.fitflow.auth.LogSecurityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.518 s -- in com.fitflow.auth.LogSecurityTest
[INFO] Running com.fitflow.auth.PasswordPolicyTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in com.fitflow.auth.PasswordPolicyTest
[INFO] Running com.fitflow.common.error.ProblemDetailTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in com.fitflow.common.error.ProblemDetailTest
[INFO] Running com.fitflow.common.filter.CorrelationIdFilterTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.009 s -- in com.fitflow.common.filter.CorrelationIdFilterTest
[INFO] Running com.fitflow.common.logging.LogRedactionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in com.fitflow.common.logging.LogRedactionTest
[INFO] Running com.fitflow.common.pagination.PageRequestDtoTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.fitflow.common.pagination.PageRequestDtoTest
[INFO] Running com.fitflow.EndpointSecurityCoverageTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.416 s -- in com.fitflow.EndpointSecurityCoverageTest
[INFO] Running com.fitflow.security.EncryptionServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.021 s -- in com.fitflow.security.EncryptionServiceTest
[INFO] Running com.fitflow.security.PermissionCheckerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.125 s -- in com.fitflow.security.PermissionCheckerTest
[INFO] Running com.fitflow.security.TokenServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.180 s -- in com.fitflow.security.TokenServiceTest
[INFO] Running com.fitflow.user.AgeServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.009 s -- in com.fitflow.user.AgeServiceTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] --- failsafe:3.5.3:integration-test (default) @ fitflow-backend ---
[INFO] Running com.fitflow.ApplicationContextIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.91 s -- in com.fitflow.ApplicationContextIT
[INFO] Running com.fitflow.auth.AuthorizationIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.825 s -- in com.fitflow.auth.AuthorizationIT
[INFO] Running com.fitflow.auth.LoginIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.510 s -- in com.fitflow.auth.LoginIT
[INFO] Running com.fitflow.auth.PasswordResetIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.431 s -- in com.fitflow.auth.PasswordResetIT
[INFO] Running com.fitflow.auth.RateLimitIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.107 s -- in com.fitflow.auth.RateLimitIT
[INFO] Running com.fitflow.auth.RefreshTokenIT
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.606 s -- in com.fitflow.auth.RefreshTokenIT
[INFO] Running com.fitflow.auth.RegisterIT
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.750 s -- in com.fitflow.auth.RegisterIT
[INFO] Running com.fitflow.ConstraintIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.111 s -- in com.fitflow.ConstraintIT
[INFO] Running com.fitflow.FlywayMysqlIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.046 s -- in com.fitflow.FlywayMysqlIT
[INFO] Running com.fitflow.FlywayPostgresIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.035 s -- in com.fitflow.FlywayPostgresIT
[INFO] Running com.fitflow.settings.SecretsServiceIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.600 s -- in com.fitflow.settings.SecretsServiceIT
[INFO] 
[INFO] Results:
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] --- jacoco:0.8.12:check (check) @ fitflow-backend ---
[INFO] Loading execution data file C:\Users\shrey\Downloads\FitFlow\backend\target\jacoco.exec
[INFO] Analyzed bundle 'fitflow-backend' with 41 classes
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

Verification Results Summary:
```text
=== Verification Results ===
Environment Variables               : PASS
MySQL Port 3306                     : PASS
PostgreSQL Port 5432                : PASS
RabbitMQ Port 5672                  : PASS
Maven Verify                        : PASS
Live Health (/actuator/health UP)   : PASS

All verification gates PASSED!
```

Total Test Count: **90 tests** (51 Unit + 39 Integration), 0 failures, 0 errors.

---

## 2. Live Application Walkthrough

Executed against a live dev server (`java -jar backend/target/fitflow-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev`):

### 2.1 Register New Member
`POST http://localhost:8080/api/v1/auth/register`
Request:
```json
{
  "email": "sarah.connor@fitflow.local",
  "password": "CyberPass2026!",
  "fullName": "Sarah Connor",
  "dateOfBirth": "1996-03-15",
  "phone": "+919876543210"
}
```
Response (`201 Created`):
```json
{
  "userId": 4,
  "email": "sarah.connor@fitflow.local",
  "fullName": "Sarah Connor"
}
```

### 2.2 Login with Wrong Password
`POST http://localhost:8080/api/v1/auth/login`
Request:
```json
{
  "email": "sarah.connor@fitflow.local",
  "password": "WrongPassword123!"
}
```
Response (`401 Unauthorized`):
```json
{
  "type": "urn:problem-type:unauthenticated",
  "title": "Unauthenticated",
  "status": 401,
  "detail": "Invalid email or password",
  "instance": "/api/v1/auth/login",
  "code": "UNAUTHENTICATED",
  "traceId": "35113e6e-f731-4b9c-a921-af762e0d9ec4"
}
```

### 2.3 Login with Right Password
`POST http://localhost:8080/api/v1/auth/login`
Request:
```json
{
  "email": "sarah.connor@fitflow.local",
  "password": "CyberPass2026!"
}
```
Response (`200 OK`):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0IiwiZW1haWwiOiJzYXJhaC5jb25ub3JAZml0Zmxvdy5sb2NhbCIsInJvbGVzIjpbIk1FTUJFUiJdLCJwZXJtaXNzaW9ucyI6W10sImlzcyI6ImZpdGZsb3ciLCJpYXQiOjE3OTMxMjg2NjEsImV4cCI6MTc5MzEyOTU2MX0.MyrIEzvZ1T0OC4FPm5Lt0c4-sXr6GisFopBFrkPsXek",
  "refreshToken": "d8SJjhqX4jweM0YmcvEjYyO5foSV2qoLaLF_0e76WAw",
  "expiresInSeconds": 900,
  "user": {
    "id": 4,
    "email": "sarah.connor@fitflow.local",
    "fullName": "Sarah Connor",
    "roles": [
      "MEMBER"
    ],
    "permissions": [],
    "mustChangePassword": false
  }
}
```

### 2.4 GET /api/v1/me/permissions (Without Token)
`GET http://localhost:8080/api/v1/me/permissions`
Response (`401 Unauthorized`):
```json
{
  "type": "urn:problem-type:unauthenticated",
  "title": "Unauthenticated",
  "status": 401,
  "detail": "Full authentication is required to access this resource.",
  "instance": "/api/v1/me/permissions",
  "code": "UNAUTHENTICATED"
}
```

### 2.5 GET /api/v1/me/permissions (With Bearer Token)
`GET http://localhost:8080/api/v1/me/permissions`
Header: `Authorization: Bearer <accessToken>`
Response (`200 OK`):
```json
{
  "roles": [
    "MEMBER"
  ],
  "permissions": []
}
```

### 2.6 Rotate Refresh Token
`POST http://localhost:8080/api/v1/auth/refresh`
Request:
```json
{
  "refreshToken": "d8SJjhqX4jweM0YmcvEjYyO5foSV2qoLaLF_0e76WAw"
}
```
Response (`200 OK`):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0IiwiZW1haWwiOiJzYXJhaC5jb25ub3JAZml0Zmxvdy5sb2NhbCIsInJvbGVzIjpbIk1FTUJFUiJdLCJwZXJtaXNzaW9ucyI6W10sImlzcyI6ImZpdGZsb3ciLCJpYXQiOjE3OTMxMjg2NjEsImV4cCI6MTc5MzEyOTU2MX0.MyrIEzvZ1T0OC4FPm5Lt0c4-sXr6GisFopBFrkPsXek",
  "refreshToken": "wcpC9yPNqcTl_20rxtJfyGfOegEim8_z_R8yLbiMB1E",
  "expiresInSeconds": 900,
  "user": {
    "id": 4,
    "email": "sarah.connor@fitflow.local",
    "fullName": "Sarah Connor",
    "roles": [
      "MEMBER"
    ],
    "permissions": [],
    "mustChangePassword": false
  }
}
```

### 2.7 Reuse Old Revoked Refresh Token (Reuse Detection)
`POST http://localhost:8080/api/v1/auth/refresh`
Request:
```json
{
  "refreshToken": "d8SJjhqX4jweM0YmcvEjYyO5foSV2qoLaLF_0e76WAw"
}
```
Response (`401 Unauthorized`):
```json
{
  "type": "urn:problem-type:unauthenticated",
  "title": "Unauthenticated",
  "status": 401,
  "detail": "Revoked refresh token reuse detected",
  "instance": "/api/v1/auth/refresh",
  "code": "UNAUTHENTICATED",
  "traceId": "f961538c-cec9-409a-ad4a-becf75d3cfae"
}
```

### 2.8 Logout (Revoke Token Family)
`POST http://localhost:8080/api/v1/auth/logout`
Header: `Authorization: Bearer <newAccessToken>`
Request:
```json
{
  "refreshToken": "wcpC9yPNqcTl_20rxtJfyGfOegEim8_z_R8yLbiMB1E"
}
```
Response (`204 No Content`)

### 2.9 Forgot Password (Existing vs Non-Existing Email)
`POST http://localhost:8080/api/v1/auth/forgot-password`

Existing email (`sarah.connor@fitflow.local`):
```json
{
  "message": "If the email is registered, a password reset link has been sent."
}
```

Non-existing email (`ghost.operator@fitflow.local`):
```json
{
  "message": "If the email is registered, a password reset link has been sent."
}
```
**Identical Bodies Check**: `True` (Both return identical HTTP 202 status and body to eliminate enumeration).

---

## 3. Three Sanity-Break Proofs

### Proof 1: Refresh-Token Reuse Detection Break
**Intervention**: Bypassed `if (token.getRevokedAt() != null)` in `RefreshTokenService.rotateRefreshToken()`.  
**Raw Failure Output**:
```text
[ERROR] Failures: 
[ERROR]   RefreshTokenIT.testReuseOfRevokedRefreshTokenRevokesEntireFamily:131 Status expected:<401> but was:<200>
[INFO] 
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```
**Restoration**: Restored the revocation verification block.  
**Restored Output**:
```text
[INFO] Running com.fitflow.auth.RefreshTokenIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.23 s -- in com.fitflow.auth.RefreshTokenIT
[INFO] BUILD SUCCESS
```

---

### Proof 2: JWT Expiry Validation Break
**Intervention**: In `TokenService.validateAndExtractClaims()`, suppressed `ExpiredJwtException` and returned `e.getClaims()` instead of throwing `ApiException.unauthenticated`.  
**Raw Failure Output**:
```text
[ERROR] Failures: 
[ERROR]   TokenServiceTest.testExpiredAccessTokenRejected:82 Expected com.fitflow.common.error.ApiException to be thrown, but nothing was thrown.
[INFO] 
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```
**Restoration**: Restored `throw ApiException.unauthenticated("Access token has expired")`.  
**Restored Output**:
```text
[INFO] Running com.fitflow.security.TokenServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.349 s -- in com.fitflow.security.TokenServiceTest
[INFO] BUILD SUCCESS
```

---

### Proof 3: EndpointSecurityCoverageTest Break
**Intervention**: Removed `@PreAuthorize("@perm.has('CLASS_EDIT')")` from `TestAdminProtectedController.classEditAction()`.  
**Raw Failure Output**:
```text
[ERROR] Failures: 
[ERROR]   EndpointSecurityCoverageTest.testAllEndpointsAreProtectedOrPublic:94 SECURITY GATE FAILURE: Unprotected endpoints found without @PreAuthorize or PublicRoutes entry:
GET /api/v1/test-admin/class-edit (handled by TestAdminProtectedController#classEditAction) ==> expected: <true> but was: <false>
[INFO] 
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```
**Restoration**: Re-applied `@PreAuthorize("@perm.has('CLASS_EDIT')")`.  
**Restored Output**:
```text
[INFO] Running com.fitflow.EndpointSecurityCoverageTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.66 s -- in com.fitflow.EndpointSecurityCoverageTest
[INFO] BUILD SUCCESS
```
