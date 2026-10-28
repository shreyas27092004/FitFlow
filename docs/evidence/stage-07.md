# FitFlow Stage 7 Evidence Report: Health Profiles, Consent (DPDP Act), Deterministic Screening & Energy Math

## 1. Architectural Summary & System Contract

Stage 7 establishes member health profiles, compliance and consent mechanisms, deterministic class screening, and energy/nutrition calculations:
- **Disclaimer Obligation**: "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use." prominently embedded into safety rule configuration headers (`safety-rules.yml`, `modifications.yml`), `CONTRACT.md` Assumptions, and serialized into every wellness API suggestion response.
- **Data Protection & DPDP Act Compliance**:
  - Plain-language consent (`v1.md`) covering distinct purposes: `PERSONALIZED_CLASSES`, `CALORIE_ESTIMATES`, `SHARE_TRAINER_SUMMARY`, and `AI_ASSISTANT_PROCESSING`.
  - Age restriction: strictly age 18+ required to grant health consent, store health profiles, or compute nutrition targets (`AgeService`).
  - Encrypted storage at rest: profile data serialized as versioned JSON and encrypted using AES-256-GCM (`HealthDataCodec`) stored in `member_health_profiles.data_encrypted VARBINARY(4096)`. Decrypted in memory only when handling requests with active consent.
  - Hard deletion & export: DPDP export returns decrypted profile, metrics, progress, feedback, and access logs. Deletion permanently erases profile, metrics, progress, and proposals, scrubs comments and pain areas from feedback, and preserves consent audit history.
  - Access logging: every read, write, export, and delete operation writes an immutable audit record to `health_access_log`.
- **Deterministic Screening & Safety**:
  - PAR-Q (Q1–Q7), 8 coded conditions, and 9 injury areas evaluated without any floating-point or non-deterministic algorithms.
  - Statuses: `CLEAR`, `CAUTION`, `RED_FLAG`, with `doctorCheckRecommended` flag.
  - Unreviewed class metadata (`metadata_reviewed = false`) is automatically blocked.
  - Gentle filtering: members with `RED_FLAG` who have not attested medical clearance are restricted to LOW intensity classes with zero contraindication movement tags.
- **Calorie & Nutrition Math**:
  - Non-floating-point calculations using `BigDecimal` with rounding applied once at the end (`RoundingMode.HALF_UP`).
  - Class energy burn: `MET * weightKg * (durationMinutes / 60)`.
  - BMR (Mifflin-St Jeor): `10*weight + 6.25*height - 5*age + s` (s: MALE=5, FEMALE=-161, UNSPECIFIED=-78).
  - TDEE: BMR multiplied by activity factor derived from planned sessions/week.
  - Calorie floor protection: never below 1200 kcal (FEMALE/UNSPECIFIED) or 1500 kcal (MALE).
  - Underweight protection: BMI < 18.5 disables deficit for `LOSE_WEIGHT`.
- **Anonymous Trainer Prep**:
  - Class session prep endpoint provides aggregated injury restriction counts and modification suggestions only if at least 3 confirmed attendees have consented to `SHARE_TRAINER_SUMMARY`. If fewer than 3 attend, summary is withheld to prevent deanonymization.
- **Architectural Isolation**:
  - ArchUnit gate enforces that only `com.fitflow.wellness` interacts with health repositories and `HealthDataCodec`.
  - Health DTOs override `toString()` returning `[REDACTED]`.

---

## 2. Mandatory Sanity Breaks

Four intentional mutations were introduced into the codebase to verify that safety barriers fail predictably when broken, and recover fully when restored.

### Break 1: Age Verification Gate (18+ Requirement for Consent)
- **Component**: `backend/src/main/java/com/fitflow/wellness/ConsentService.java`
- **Break Mechanism**: Commented out the `ageService.is18Plus(user.getDateOfBirth())` check in `grantConsent`.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.wellness.ConsentIT
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 19.41 s <<< FAILURE! -- in com.fitflow.wellness.ConsentIT
  [ERROR] com.fitflow.wellness.ConsentIT.test17YearOldRejectedFromGrantingConsent -- Time elapsed: 1.144 s <<< FAILURE!
  java.lang.AssertionError: Status expected:<403> but was:<200>
      at org.springframework.test.util.AssertionErrors.fail(AssertionErrors.java:61)
      at org.springframework.test.util.AssertionErrors.assertEquals(AssertionErrors.java:128)
      at org.springframework.test.web.servlet.result.StatusResultMatchers.lambda$matcher$9(StatusResultMatchers.java:640)
      at com.fitflow.wellness.ConsentIT.test17YearOldRejectedFromGrantingConsent(ConsentIT.java:106)
  ```
- **Resolution**: Restored age verification guard in `ConsentService`.
- **Pass Verification**: `ConsentIT` passed (6/6 tests passing).

---

### Break 2: Mifflin-St Jeor Formula Constant Integrity
- **Component**: `backend/src/main/java/com/fitflow/wellness/CalorieService.java`
- **Break Mechanism**: Modified FEMALE BMR adjustment factor `s` from `-161` to `-151`.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.wellness.CalorieMathTest
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.099 s <<< FAILURE! -- in com.fitflow.wellness.CalorieMathTest
  [ERROR] com.fitflow.wellness.CalorieMathTest.testPromptVector2 -- Time elapsed: 0.055 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: expected: <1345> but was: <1355>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)
      at org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:197)
      at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:182)
      at com.fitflow.wellness.CalorieMathTest.testPromptVector2(CalorieMathTest.java:79)
  ```
- **Resolution**: Restored `case FEMALE -> -161;`.
- **Pass Verification**: `CalorieMathTest` passed (17/17 tests passing, including 1000-case property test).

---

### Break 3: Encryption at Rest Enforcement
- **Component**: `backend/src/main/java/com/fitflow/wellness/HealthDataCodec.java`
- **Break Mechanism**: Disabled AES-GCM encryption in `encode()`, returning unencrypted JSON bytes directly.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.wellness.HealthProfileStorageTest
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 16.47 s <<< FAILURE! -- in com.fitflow.wellness.HealthProfileStorageTest
  [ERROR] com.fitflow.wellness.HealthProfileStorageTest.testRawSqlReadContainsNoPlaintext -- Time elapsed: 0.576 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: Raw DB blob must not contain 'KNEE' ==> expected: <false> but was: <true>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)
      at org.junit.jupiter.api.AssertFalse.failNotFalse(AssertFalse.java:63)
      at org.junit.jupiter.api.AssertFalse.assertFalse(AssertFalse.java:36)
      at com.fitflow.wellness.HealthProfileStorageTest.testRawSqlReadContainsNoPlaintext(HealthProfileStorageTest.java:143)
  ```
- **Resolution**: Restored AES-256-GCM encryption in `HealthDataCodec.encode()`.
- **Pass Verification**: `HealthProfileStorageTest` passed (3/3 tests passing).

---

### Break 4: Minimum Consenting Attendee Privacy Gate (< 3 Rule)
- **Component**: `backend/src/main/java/com/fitflow/wellness/TrainerPrepService.java`
- **Break Mechanism**: Changed minimum consenting attendee threshold from `< 3` to `< 2`.
- **Failure Output**:
  ```text
  [INFO] Running com.fitflow.wellness.TrainerPrepTest
  [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 3.583 s <<< FAILURE! -- in com.fitflow.wellness.TrainerPrepTest
  [ERROR] com.fitflow.wellness.TrainerPrepTest.testFewerThan3ConsentingAttendees -- Time elapsed: 3.138 s <<< FAILURE!
  org.opentest4j.AssertionFailedError: summaryAvailable must be false when < 3 consenting attendees ==> expected: <false> but was: <true>
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)
      at org.junit.jupiter.api.AssertFalse.failNotFalse(AssertFalse.java:63)
      at org.junit.jupiter.api.AssertFalse.assertFalse(AssertFalse.java:36)
      at com.fitflow.wellness.TrainerPrepTest.testFewerThan3ConsentingAttendees(TrainerPrepTest.java:131)
  ```
- **Resolution**: Restored `if (includedProfiles.size() < 3)` threshold.
- **Pass Verification**: `TrainerPrepTest` passed (3/3 tests passing).

---

## 3. Live Walkthrough Evidence (`Stage7WalkthroughIT`)

Executed via `com.fitflow.wellness.Stage7WalkthroughIT`:

### Step 1: Over-18 Member Reads Consent (`GET /api/v1/wellness/consent`)
```http
GET /api/v1/wellness/consent HTTP/1.1
Authorization: Bearer <member_token>
```
**Response (200 OK)**:
```json
{
  "version": "v1",
  "text": "# Health & Wellness Data Consent\n\nWelcome to FitFlow Wellness...",
  "purposes": [
    "PERSONALIZED_CLASSES",
    "CALORIE_ESTIMATES",
    "SHARE_TRAINER_SUMMARY",
    "AI_ASSISTANT_PROCESSING"
  ],
  "granted": [],
  "eligible": true
}
```
*Confirmed text includes: "Consent wording should be confirmed against your institution's requirements and India's DPDP Act obligations."*

---

### Step 2: Member Grants Purposes (`POST /api/v1/wellness/consent`)
```http
POST /api/v1/wellness/consent HTTP/1.1
Content-Type: application/json
Authorization: Bearer <member_token>

{
  "version": "v1",
  "purposes": ["PERSONALIZED_CLASSES", "CALORIE_ESTIMATES"]
}
```
**Response**: `200 OK` (Emits outbox event `consent.granted`).

---

### Step 3: Member Saves Health Profile (`PUT /api/v1/wellness/profile`)
```http
PUT /api/v1/wellness/profile HTTP/1.1
Content-Type: application/json
Authorization: Bearer <member_token>

{
  "experienceLevel": "INTERMEDIATE",
  "pastExperienceNote": "Past ACL surgery on right knee in 2023",
  "goals": ["LOSE_WEIGHT"],
  "sessionsPerWeekPlanned": 3,
  "availability": [],
  "injuries": [{ "area": "KNEE", "severity": "MODERATE" }],
  "conditions": [],
  "parq": { "Q1": false, "Q2": false, "Q3": false, "Q4": false, "Q5": false, "Q6": false, "Q7": false },
  "medicalClearanceAttested": true,
  "heightCm": 175.0,
  "weightKg": 70.0,
  "sex": "MALE",
  "version": 1
}
```
**Response (200 OK)**:
```json
{
  "profile": {
    "experienceLevel": "INTERMEDIATE",
    "pastExperienceNote": "Past ACL surgery on right knee in 2023",
    "goals": ["LOSE_WEIGHT"],
    "sessionsPerWeekPlanned": 3,
    "availability": [],
    "injuries": [{ "area": "KNEE", "severity": "MODERATE" }],
    "conditions": [],
    "parq": { "Q1": false, "Q2": false, "Q3": false, "Q4": false, "Q5": false, "Q6": false, "Q7": false },
    "medicalClearanceAttested": true,
    "heightCm": 175.0,
    "weightKg": 70.0,
    "sex": "MALE",
    "version": 1
  },
  "screening": {
    "status": "CAUTION",
    "reasons": ["INJURY:KNEE:MODERATE"],
    "doctorCheckRecommended": false
  }
}
```

---

### Step 4: Class Safety Filtering (`GET /api/v1/wellness/classes/safe`)
```http
GET /api/v1/wellness/classes/safe HTTP/1.1
Authorization: Bearer <member_token>
```
**Response (200 OK)**:
```json
{
  "doctorCheckRecommended": false,
  "disclaimer": "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use.",
  "eligible": [
    {
      "session": { "id": 100225, "className": "Gentle Flow Yoga", "category": "YOGA", "intensity": "LOW", "impactLevel": "LOW" },
      "verdict": "CAUTION",
      "reasonCodes": ["SCREENING_CAUTION"],
      "humanReasons": ["Suitable with care. Please listen to your body and request modifications if needed."]
    }
  ],
  "avoid": [
    {
      "session": { "id": 100224, "className": "High-Intensity Bootcamp", "category": "HIIT", "intensity": "HIGH", "impactLevel": "HIGH" },
      "verdict": "BLOCKED",
      "reasonCodes": [
        "TAG_CONFLICT:deep_flexion(KNEE_MODERATE)",
        "TAG_CONFLICT:high_impact(KNEE_MODERATE)"
      ],
      "humanReasons": [
        "Not suggested for now: this class includes deep flexion movement and you listed a knee moderate.",
        "Not suggested for now: this class includes high-impact movement and you listed a knee moderate."
      ]
    }
  ]
}
```

---

### Step 5: Nutrition Numbers Calculation (`GET /api/v1/wellness/nutrition/numbers`)
```http
GET /api/v1/wellness/nutrition/numbers HTTP/1.1
Authorization: Bearer <member_token>
```
**Response (200 OK)**:
```json
{
  "available": true,
  "bmr": 1659,
  "tdee": 2281,
  "targetCalories": 1939,
  "floorApplied": false,
  "notes": [],
  "disclaimer": "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use."
}
```
**Manual Hand Calculation Verification**:
- User: Male, 28 years, 70 kg, 175 cm, 3 planned sessions/week, Goal = LOSE_WEIGHT.
- BMR = `10 * 70 + 6.25 * 175 - 5 * 28 + 5` = `700 + 1093.75 - 140 + 5` = `1658.75` -> rounded HALF_UP = **1659 kcal** (Match).
- Activity factor for 3 sessions/week = `1.375`.
- TDEE = `1658.75 * 1.375` = `2280.78125` -> rounded HALF_UP = **2281 kcal** (Match).
- LOSE_WEIGHT Deficit = `min(0.15 * 2280.78125, 500)` = `min(342.117, 500)` = `342.117 kcal`.
- Target Calories = `2280.78125 - 342.1171875` = `1938.664` -> rounded HALF_UP = **1939 kcal** (Match).
- Male floor check: `1939 > 1500` -> `floorApplied = false` (Match).

---

### Step 6: Direct SQL Read Confirming Ciphertext at Rest
```sql
SELECT HEX(LEFT(data_encrypted, 40)) FROM member_health_profiles WHERE member_id = 110276;
```
**Database Output**:
```text
01FE23CF6CEA3AEEFB13363CEF685F9F9B00930837B45F597D5FF05D3A9BCB3030C7199B08E20673
```
- Direct string scanning over raw database bytes confirmed **zero** occurrences of `"knee"`, `"moderate"`, `"surgery"`, `"acl"`, `"175.0"`, or `"70.0"`.

---

### Step 7: Feedback Submission & Anonymous Event Emission
```http
POST /api/v1/bookings/100069/feedback HTTP/1.1
Content-Type: application/json
Authorization: Bearer <member_token>

{
  "rating": 3,
  "feltPain": true,
  "painArea": "KNEE",
  "comment": "Sharp twinge in the patella during warrior pose"
}
```
**Response (200 OK)**:
```json
{
  "proposalId": 7,
  "message": "Would you like to add this to your profile?"
}
```
**Verification in Database**:
- Restriction proposal generated in `restriction_proposals`: `id = 7, area = "KNEE", suggestedSeverity = "MILD", status = "PENDING"`.
- Anonymous outbox event generated in `outbox_events`:
  - `eventType`: `feedback.pain_reported`
  - `aggregateType`: `SESSION`
  - `aggregateId`: `100226`
  - `payload`: `{"areaCode":"KNEE","classId":100357,"sessionId":100226}`
  - Confirmed: **zero member identity or personal information** present in outbox payload.

---

### Step 8: Member Data Export (`GET /api/v1/wellness/export`)
```http
GET /api/v1/wellness/export HTTP/1.1
Authorization: Bearer <member_token>
```
**Response (200 OK)**:
```json
{
  "profile": {
    "experienceLevel": "INTERMEDIATE",
    "pastExperienceNote": "Past ACL surgery on right knee in 2023",
    "goals": ["LOSE_WEIGHT"],
    "sessionsPerWeekPlanned": 3,
    "availability": [],
    "injuries": [{ "area": "KNEE", "severity": "MODERATE" }],
    "conditions": [],
    "parq": { "Q1": false, "Q2": false, "Q3": false, "Q4": false, "Q5": false, "Q6": false, "Q7": false },
    "medicalClearanceAttested": true,
    "heightCm": 175.0,
    "weightKg": 70.0,
    "sex": "MALE",
    "version": 1
  },
  "metrics": [
    { "id": 28, "recordedOn": "2026-10-28", "weightKg": 70.0, "heightCm": 175.0 }
  ],
  "progress": [],
  "feedback": [
    { "id": 6, "bookingId": 100069, "rating": 3, "feltPain": true, "painAreaCode": "KNEE", "comment": "Sharp twinge in the patella during warrior pose" }
  ],
  "consentHistory": [
    { "id": 37, "consentVersion": "v1", "purpose": "CALORIE_ESTIMATES" },
    { "id": 36, "consentVersion": "v1", "purpose": "PERSONALIZED_CLASSES" }
  ],
  "accessLogs": [
    { "id": 58, "action": "READ", "purpose": "NUTRITION_NUMBERS_CALCULATION" },
    { "id": 57, "action": "READ", "purpose": "SAFE_CLASSES_QUERY" },
    { "id": 56, "action": "WRITE", "purpose": "PROFILE_SAVE" },
    { "id": 55, "action": "WRITE", "purpose": "CONSENT_GRANT" }
  ]
}
```

---

### Step 9: Hard Deletion of Health Data (`DELETE /api/v1/wellness/data`)
```http
DELETE /api/v1/wellness/data HTTP/1.1
Content-Type: application/json
Authorization: Bearer <member_token>

{
  "confirm": "DELETE MY HEALTH DATA"
}
```
**Response**: `200 OK`
**Verification**:
1. `SELECT COUNT(*) FROM member_health_profiles WHERE member_id = 110276` -> **0**.
2. `GET /api/v1/wellness/profile` -> **404 Not Found**.
3. `GET /api/v1/wellness/export` -> `profile: null`, `consentHistory: [...]` (preserved for regulatory auditing).
4. Outbox event `health.data_deleted` emitted.

---

## 4. Test Suite Summary

- `ScreeningServiceTest`: 21 passed
- `SafetyFilterTest`: 47 passed
- `CalorieMathTest`: 17 passed
- `LlmSafeProfileTest`: 4 passed
- `HealthProfileStorageTest`: 3 passed
- `WeeklyProgressTest`: 3 passed
- `FeedbackAndProposalsTest`: 5 passed
- `TrainerPrepTest`: 3 passed
- `WellnessLogSecurityTest`: 1 passed
- `DataRightsTest`: 2 passed
- `ConsentIT`: 6 passed
- `AdminWellnessIsolationTest`: 1 passed
- `Stage7WalkthroughIT`: 1 passed
- `ArchitectureRulesTest`: 10 passed (including Gate j health isolation)
- `EndpointSecurityCoverageTest`: passed (all endpoints covered by `@PreAuthorize` security checks)
- **Total Stage 7 & Governance Tests**: **125 passed, 0 failures, 0 errors**.
