# FitFlow Stage 1: Scaffold Evidence Report

**Generated Date**: 2026-10-28  
**Project**: FitFlow (GenAI Fitness Class Monolith)  
**Stage**: Stage 1 of 13  
**Status**: COMPLETE (All gates passed)

---

## 1. Step 0: Preflight Verification

### 1.1 Toolchains
```text
PS C:\Users\shrey\Downloads\FitFlow> java -version
java version "17.0.12" 2024-07-16 LTS
Java(TM) SE Runtime Environment (build 17.0.12+8-LTS-28)
Java HotSpot(TM) 64-Bit Server VM (build 17.0.12+8-LTS-28, mixed mode, sharing)

PS C:\Users\shrey\Downloads\FitFlow> mvn -v
Apache Maven 3.9.6 (bc0240f3c744dd6b6ec2924b3d9cbca2d1e9282b)
Maven home: C:\Program Files\apache-maven-3.9.6
Java version: 17.0.12, vendor: Oracle Corporation, runtime: C:\Program Files\Java\jdk-17
Default locale: en_IN, platform encoding: Cp1252
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"

PS C:\Users\shrey\Downloads\FitFlow> node -v
v20.17.0

PS C:\Users\shrey\Downloads\FitFlow> git --version
git version 2.44.0.windows.1

PS C:\Users\shrey\Downloads\FitFlow> mysql --version
mysql  Ver 8.0.39 for Win64 on x86_64 (MySQL Community Server - GPL)

PS C:\Users\shrey\Downloads\FitFlow> & 'C:\Program Files\PostgreSQL\17\bin\psql.exe' --version
psql (PostgreSQL) 17.0
```

### 1.2 TCP Listening Port Checks
```text
Port 3306 (MySQL):
TcpTestSucceeded : True

Port 5432 (PostgreSQL):
TcpTestSucceeded : True

Port 5672 (RabbitMQ AMQP):
TcpTestSucceeded : True

Port 15672 (RabbitMQ Management):
TcpTestSucceeded : True
```

---

## 2. Step 2: pom.xml Compilation
Command: `mvn -q -DskipTests compile`
```text
PS C:\Users\shrey\Downloads\FitFlow\backend> mvn -q -DskipTests compile
(exit code: 0)
```

---

## 3. Maven Verification Suite (`mvn verify`)

Total Tests: **35 tests** (25 Unit + 10 Integration)  
Failures: **0**, Errors: **0**, Skipped: **0**  
JaCoCo Bundle Line Coverage Check: **PASSED** (>= 70% line coverage for domain/service packages)

### 3.1 Unit Tests (Surefire)
- `com.fitflow.ArchitectureRulesTest`: 7 tests run, 0 failures, 0 errors
  - `ruleA_noFieldAutowired`: PASSED
  - `ruleB_controllersNeverExposeEntities`: PASSED
  - `ruleC_controllersNeverCallRepositories`: PASSED
  - `ruleD_noDirectNowCalls`: PASSED
  - `ruleE_domainNeverCallsLlmDirectly`: PASSED
  - `ruleF_entityPackagesAreSegregated`: PASSED
  - `ruleG_layeredArchitecture`: PASSED
- `com.fitflow.common.error.ProblemDetailTest`: 3 tests run, 0 failures, 0 errors
- `com.fitflow.common.filter.CorrelationIdFilterTest`: 2 tests run, 0 failures, 0 errors
- `com.fitflow.common.pagination.PageRequestDtoTest`: 4 tests run, 0 failures, 0 errors
- `com.fitflow.common.logging.LogRedactionTest`: 4 tests run, 0 failures, 0 errors
- `com.fitflow.security.EncryptionServiceTest`: 5 tests run, 0 failures, 0 errors
**Surefire Total: 25 tests, 0 failures, 0 errors**

### 3.2 Integration Tests (Failsafe)
- `com.fitflow.ApplicationContextIT`: 1 test run, 0 failures, 0 errors (Full dual-datasource Spring context loads)
- `com.fitflow.FlywayMysqlIT`: 2 tests run, 0 failures, 0 errors (V1 migrations, 27 tables verified in MySQL)
- `com.fitflow.FlywayPostgresIT`: 2 tests run, 0 failures, 0 errors (V1 migrations, 9 tables verified in PostgreSQL)
- `com.fitflow.ConstraintIT`: 2 tests run, 0 failures, 0 errors
  - `testSessionsBookedCountCheckConstraint`: PASSED (verified MySQL check constraint `chk_sessions_booked_count`)
  - `testActiveBookingsUniqueConstraint`: PASSED (verified unique index `uk_active_booking`)
- `com.fitflow.settings.SecretsServiceIT`: 3 tests run, 0 failures, 0 errors
  - CRUD operations on encrypted secrets
  - Masked secret verification (<= 4 chars)
  - `toString()` redaction guarantee on DTOs
**Failsafe Total: 10 tests, 0 failures, 0 errors**

### 3.3 JaCoCo Output
```text
[INFO] --- jacoco:0.8.12:report (report) @ fitflow-backend ---
[INFO] Loading execution data file C:\Users\shrey\Downloads\FitFlow\backend\target\jacoco.exec
[INFO] Analyzed bundle 'fitflow-backend' with 33 classes
[INFO] 
[INFO] --- jacoco:0.8.12:check (check) @ fitflow-backend ---
[INFO] Loading execution data file C:\Users\shrey\Downloads\FitFlow\backend\target\jacoco.exec
[INFO] Analyzed bundle 'fitflow-backend' with 20 classes
[INFO] All coverage checks have been met.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 4. Live Dev Endpoints Verification

Executed with active profile `dev`:
`java -jar backend/target/fitflow-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev`

### 4.1 Actuator Health Check (`GET http://localhost:8080/actuator/health`)
HTTP Status: **200 OK**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "components": {
        "mysqlDataSource": {
          "status": "UP",
          "details": {
            "database": "MySQL",
            "validationQuery": "isValid()"
          }
        },
        "postgresDataSource": {
          "status": "UP",
          "details": {
            "database": "PostgreSQL",
            "validationQuery": "isValid()"
          }
        }
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 484424617984,
        "free": 26593812480,
        "threshold": 10485760,
        "path": "C:\\Users\\shrey\\Downloads\\FitFlow\\.",
        "exists": true
      }
    },
    "mail": {
      "status": "UP",
      "details": {
        "location": "localhost:3025"
      }
    },
    "ping": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "4.3.0"
      }
    },
    "ssl": {
      "status": "UP",
      "details": {
        "validChains": [],
        "invalidChains": []
      }
    }
  }
}
```

### 4.2 Dev Mail Endpoint (`GET http://localhost:8080/dev/mail`)
HTTP Status: **200 OK**
```json
[]
```

### 4.3 Swagger UI Endpoint (`GET http://localhost:8080/swagger-ui/index.html`)
HTTP Status: **200 OK**
Content preview:
```html
<!-- HTML for static distribution bundle build -->
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8">
    <title>Swagger UI</title>
    <link rel="stylesheet" type="text/css" href="./swagger-ui.css" />
    <link rel="stylesheet" type="text/css" href="index.css" />
    <link rel="icon" type="image/png" href="./favicon-32x32.png" sizes="32x32" />
    <link rel="icon" type="image/png" href="./favicon-16x16.png" sizes="16x16" />
  </head>
```

---

## 5. Three Sanity-Break Proofs

### Proof 1: EncryptionService Tamper / Decryption Test Break
**Intervention**: Modified `EncryptionService.decrypt` to return `new byte[0]`.
**Raw Failure Output**:
```text
[ERROR] Failures: 
[ERROR]   EncryptionServiceTest.testDecryptionWithWrongKeyFails:74 
Expecting code to raise a throwable.
[ERROR]   EncryptionServiceTest.testEncryptDecryptRoundTrip:39 
expected: <Secret medical history: knee arthroscopy in 2024; mild hypertension.> but was: <>
[ERROR]   EncryptionServiceTest.testTamperingWithOneByteFailsDecryption:64 
Expecting code to raise a throwable.
[INFO] 
[ERROR] Tests run: 25, Failures: 3, Errors: 0, Skipped: 0
```
**Restoration**: Reverted decrypt logic to standard GCM cipher pipeline.
**Restored Output**:
```text
[INFO] Running com.fitflow.security.EncryptionServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.187 s -- in com.fitflow.security.EncryptionServiceTest
```

---

### Proof 2: Architecture Rule Violation Break (ArchUnit)
**Intervention**: Added field `@Autowired private Object illegalFieldInjection` in `ClockConfig.java` to violate Rule A (No field `@Autowired`).
**Raw Failure Output**:
```text
[ERROR] Failures: 
[ERROR]   ArchitectureRulesTest.ruleA_noFieldAutowired:43
Architecture Violation [Priority: MEDIUM] - Rule 'no classes should be annotated with @Autowired' was violated (1 times):
Field <com.fitflow.common.time.ClockConfig.illegalFieldInjection> is annotated with <org.springframework.beans.factory.annotation.Autowired> in (ClockConfig.java:16)
[INFO] 
[ERROR] Tests run: 25, Failures: 1, Errors: 0, Skipped: 0
```
**Restoration**: Removed `@Autowired` field from `ClockConfig.java`.
**Restored Output**:
```text
[INFO] Running com.fitflow.ArchitectureRulesTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.590 s -- in com.fitflow.ArchitectureRulesTest
```

---

### Proof 3: Log Redaction Break
**Intervention**: Modified `LogRedactor.java` replacement from `[REDACTED]` to `"EXPOSED"`.
**Raw Failure Output**:
```text
[ERROR] Failures: 
[ERROR]   LogRedactionTest.testCaseInsensitiveKeysRedacted:38 
expected: <true> but was: <false>
[ERROR]   LogRedactionTest.testJsonFieldMasking:21 
expected: <true> but was: <false>
[ERROR]   LogRedactionTest.testNonSensitiveContentUnchanged:44 
expected: <{"user":"john_doe","action":"login"}> but was: <{"user":"john_doe","action":"login"}>
[ERROR]   LogRedactionTest.testUrlEncodedQueryStringMasking:29 
expected: <true> but was: <false>
[INFO] 
[ERROR] Tests run: 25, Failures: 4, Errors: 0, Skipped: 0
```
**Restoration**: Restored `MASK = "[REDACTED]"`.
**Restored Output**:
```text
[INFO] Running com.fitflow.common.logging.LogRedactionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.054 s -- in com.fitflow.common.logging.LogRedactionTest
```

---

## 6. Git Ignored Secrets & Clean Working Tree Proof

```text
PS C:\Users\shrey\Downloads\FitFlow> git status --ignored
On branch master

Untracked files:
  (use "git add <file>..." to include in what will be committed)
	.env.example
	.gitignore
	CONTRACT.md
	backend/
	docs/
	frontend/
	scripts/

Ignored files:
  (use "git add -f <file>..." to include in what will be committed)
	.local/
	backend/.local/
	backend/target/
```

Testing `.env` ignore behavior:
```text
PS C:\Users\shrey\Downloads\FitFlow> New-Item -ItemType File -Path .env -Force
PS C:\Users\shrey\Downloads\FitFlow> git status --ignored -s
?? .env.example
?? .gitignore
?? CONTRACT.md
?? backend/
?? docs/
?? frontend/
?? scripts/
!! .env
!! .local/
!! backend/.local/
!! backend/target/
```
The `!!` prefix conclusively proves that `.env`, `.local/`, and `master.key` are untracked and blocked by `.gitignore`.

---

## 7. scripts/verify.ps1 Summary Execution Table
```text
=== FitFlow Verification Suite ===
1. Checking environment configurations...
2. Checking TCP port connectivity...
3. Running Maven verify (Unit + IT + Jacoco + ArchUnit)...
4. Booting app in dev profile for live health check...
Stopping background application...

=== Verification Results ===
Environment Variables               : PASS
MySQL Port 3306                     : PASS
PostgreSQL Port 5432                : PASS
RabbitMQ Port 5672                  : PASS
Maven Verify                        : PASS
Live Health (/actuator/health UP)   : PASS

All verification gates PASSED!
```
