# FitFlow Architecture & System Contract (CONTRACT.md)

This contract is the single source of truth for the FitFlow platform architecture, technology choices, database models, events, interfaces, and progress logs across all stages.

---

## 1. Tech Decisions & Library Versions

The following versions have been resolved and pinned via Maven dependency management (`mvn dependency:tree`):

| Technology / Library | Group / Artifact | Version | Purpose / Scope |
| :--- | :--- | :--- | :--- |
| **Java Platform** | OpenJDK | `17.0.12 LTS` | Runtime baseline (amended from Java 21 per user sign-off) |
| **Spring Boot** | `org.springframework.boot:spring-boot-starter-parent` | `3.5.3` | Core monolith framework |
| **Spring Framework** | `org.springframework:spring-core` | `6.2.8` | Core application container |
| **Spring Security** | `org.springframework.security:spring-security-core` | `6.5.1` | Security filters, crypto, and access controls |
| **Flyway Core** | `org.flywaydb:flyway-core` | `11.7.2` | Database migration engine |
| **Flyway MySQL** | `org.flywaydb:flyway-mysql` | `11.7.2` | MySQL migration dialect support |
| **Flyway Postgres** | `org.flywaydb:flyway-database-postgresql` | `11.7.2` | PostgreSQL migration dialect support |
| **MySQL Connector** | `com.mysql:mysql-connector-j` | `9.2.0` | Transactional database JDBC driver |
| **PostgreSQL Driver** | `org.postgresql:postgresql` | `42.7.7` | Agent memory & audit database JDBC driver |
| **Springdoc OpenAPI** | `org.springdoc:springdoc-openapi-starter-webmvc-ui`| `2.8.5` | OpenAPI 3.0 specification & Swagger UI (5.18.3) |
| **JJWT API** | `io.jsonwebtoken:jjwt-api` | `0.12.6` | Stateless JWT token validation & generation |
| **JJWT Impl** | `io.jsonwebtoken:jjwt-impl` | `0.12.6` | JJWT runtime engine |
| **JJWT Jackson** | `io.jsonwebtoken:jjwt-jackson` | `0.12.6` | JJWT JSON serializer |
| **Bucket4j Core** | `com.bucket4j:bucket4j-core` | `8.10.1` | Token bucket rate limiting engine |
| **Lombok** | `org.projectlombok:lombok` | `1.18.38` | Boilerplate reduction (@Getter, @Setter, @Builder) |
| **Logstash Encoder** | `net.logstash.logback:logstash-logback-encoder` | `8.0` | Production JSON structured logging |
| **GreenMail** | `com.icegreen:greenmail` | `2.1.3` | Development & test in-memory SMTP server |
| **ArchUnit** | `com.tngtech.archunit:archunit-junit5` | `1.4.0` | Automated architectural rule enforcement |
| **Awaitility** | `org.awaitility:awaitility` | `4.2.2` | Asynchronous assertion engine for integration testing |
| **Surefire Plugin** | `org.apache.maven.plugins:maven-surefire-plugin` | `3.5.3` | Unit test execution |
| **Failsafe Plugin** | `org.apache.maven.plugins:maven-failsafe-plugin` | `3.5.3` | Integration test execution (*IT) |
| **Jacoco Plugin** | `org.jacoco:jacoco-maven-plugin` | `0.8.12` | Code coverage reporting & verification (>= 70%) |
| **Enforcer Plugin** | `org.apache.maven.plugins:maven-enforcer-plugin` | `3.5.0` | Enforces Java 17 and bans duplicate POM dependencies |

---

## 2. Package Map

Base package: `com.fitflow`

| Sub-package | Purpose |
| :--- | :--- |
| `common` | Cross-cutting utilities: Error handling, Clock bean, base entities, pagination, and correlation ID. |
| `config` | Dual DataSource beans, JPA configurations, Flyway migrators, OpenAPI, and security chain. |
| `security` | Security filters, AES-256-GCM encryption service, password encoder, and auth rules. |
| `auth` | Authentication workflows, login, token refresh, password reset tokens, and rotation. |
| `user` | User account management, roles, and administrative controls. |
| `trainer` | Trainer profiles, bio, specialties, availability, and session pay rates. |
| `fitnessclass` | Fitness class catalog, categories, intensity, impact, MET values, and contraindication tags. |
| `session` | Scheduled class sessions, room assignments, capacities, and booking counts. |
| `booking` | Bookings, waitlist entries, attendance records, and booking rule validations. |
| `membership` | Membership plans, subscriptions, quota tracking, upgrades, and prorated calculations. |
| `payment` | Payment processing, idempotency, gateway references, and refund lifecycle. |
| `payout` | Trainer per-session earnings calculation, payout batches, and payout approval lifecycle. |
| `wellness` | Consent-governed member health profiles, encrypted biometrics, progress logs, and safety evaluations. |
| `agent` | Multi-agent AI conversational workflows, memory, tool orchestration, and human-in-the-loop audit logs. |
| `notification`| Transactional outbox notifications, email dispatches, and notification logs. |
| `messaging` | RabbitMQ asynchronous messaging topology, outbox polling, and in-memory fallback. |
| `admin` | Administrative audit logging, elevated action controls, and operational governance. |
| `settings` | Dynamic application settings and encrypted application secrets. |
| `devtools` | Development utilities, seed data runner, GreenMail web viewer, and diagnostics. |

---

## 3. Error Codes (RFC 7807)

Every API error returns `application/problem+json` with fields: `type`, `title`, `status`, `detail`, `instance`, `code`, `traceId`, and optionally an `errors` array of `{field, message}`.

| Error Code | HTTP Status | Description |
| :--- | :--- | :--- |
| `VALIDATION_FAILED` | 400 Bad Request | Request body validation constraint failed (includes `errors` array). |
| `UNAUTHENTICATED` | 401 Unauthorized | Missing, expired, or invalid authentication credentials. |
| `FORBIDDEN` | 403 Forbidden | Authenticated actor lacks required permission or role. |
| `NOT_FOUND` | 404 Not Found | Requested entity was not found. |
| `CONFLICT` | 409 Conflict | Resource state conflict (e.g., duplicate unique constraint or duplicate booking). |
| `OPTIMISTIC_LOCK` | 409 Conflict | Concurrent update detected via JPA `@Version`. |
| `RATE_LIMITED` | 429 Too Many Requests| Rate limit exceeded via Bucket4j token bucket. |
| `UPGRADE_REQUIRED` | 402 Payment Required | Operation requires a paid or higher membership tier. |
| `SESSION_FULL` | 409 Conflict | Class session capacity has been reached. |
| `BOOKING_RULE_VIOLATION` | 422 Unprocessable | Business booking rule violated (e.g. cutoff window, double-booking). |
| `IDEMPOTENCY_REPLAY_MISMATCH`| 422 Unprocessable | Idempotency key reused with different request payload. |
| `PAYMENT_FAILED` | 402 Payment Required | Payment gateway processing failed or declined. |
| `CONSENT_REQUIRED` | 428 Precondition Required | Explicit health consent required before storing or accessing biometric data. |
| `AGE_RESTRICTED` | 400 Bad Request | User does not meet minimum age requirement for class or plan (< 14 for registration). |
| `LOCKED` | 429 Too Many Requests| Account temporarily locked due to 5 consecutive login failures (includes Retry-After header). |
| `INTERNAL_ERROR` | 500 Internal Error | Unexpected server error. Message is masked; details hidden from clients. |

---

## 4. Tables & Owning Modules

### MySQL 8 (Transactional Database)

| Table Name | Owning Module | Optimistic Lock (v) | Purpose |
| :--- | :--- | :---: | :--- |
| `users` | `user` | Yes (`version`) | Member, Trainer, and Admin user accounts and statuses. |
| `user_roles` | `user` | No | Join table linking users to roles (`MEMBER`, `TRAINER`, `ADMIN`). |
| `admin_permissions` | `user` | No | Specific granular permissions granted to administrative users. |
| `refresh_tokens` | `auth` | No | Hashed refresh tokens with family tracking for reuse detection. |
| `password_reset_tokens`| `auth` | No | Hashed single-use password reset tokens with expiry. |
| `trainer_profiles` | `trainer` | Yes (`version`) | Bio, specialties JSON, certifications JSON, rates in paise, availability. |
| `fitness_classes` | `fitnessclass`| Yes (`version`) | Class metadata, intensity, impact, MET value, contraindication tags. |
| `class_sessions` | `session` | Yes (`version`) | Scheduled session instances, room, capacity, booked_count CHECK constraint. |
| `bookings` | `booking` | Yes (`version`) | Member bookings with active_flag unique index per session/member. |
| `waitlist_entries` | `booking` | No | Ordered FIFO waitlist for filled class sessions. |
| `attendance_records` | `booking` | No | Marked attendance or no-show per booking. |
| `membership_plans` | `membership` | Yes (`version`) | Plan catalog, prices in paise, session quotas, free tier flags. |
| `memberships` | `membership` | Yes (`version`) | User plan subscriptions, quotas used, validity windows. |
| `membership_changes` | `membership` | No | Proration audit log for plan upgrades, downgrades, and refunds. |
| `payments` | `payment` | No | Monetary transactions, gateway references, idempotency keys. |
| `refunds` | `payment` | No | Refund requests, approval states, idempotency keys. |
| `invoices` | `payment` | No | Tax-compliant invoice numbers and total amounts in paise. |
| `trainer_payouts` | `payout` | Yes (`version`) | Payout batches for trainers across date periods. |
| `trainer_session_earnings`| `payout` | No | Earnings per session linked to payout batches. |
| `outbox_events` | `notification`| No | Transactional outbox events for guaranteed message delivery. |
| `member_health_profiles`| `wellness` | Yes (`version`) | Encrypted JSON blob containing sensitive member health data. |
| `health_consents` | `wellness` | No | Audit trail of health data consents granted or revoked. |
| `body_metrics` | `wellness` | No | Encrypted biometric measurements (weight, height). |
| `progress_logs` | `wellness` | No | Encrypted workout and wellness journal entries. |
| `class_feedback` | `wellness` | No | Post-session ratings, pain detection, and encrypted comments. |
| `admin_action_log` | `admin` | No | Audit log of all sensitive admin actions with before/after diffs. |
| `app_secrets` | `settings` | No | AES-256-GCM encrypted dynamic secrets (e.g. Gemini API key). |
| `app_settings` | `settings` | No | Non-secret system settings (model names, policies). |

### PostgreSQL 16/17 (Agent Memory & Audit Database)

| Table Name | Owning Module | Purpose |
| :--- | :--- | :--- |
| `agent_conversations` | `agent` | Multi-agent conversation session threads by user and role. |
| `agent_messages` | `agent` | Chat messages exchanged between users, agents, and tool invocations. |
| `agent_memory` | `agent` | Long-term episodic and semantic user memory stored as JSONB. |
| `agent_action_audit` | `agent` | Redacted log of all AI tool executions and human-in-the-loop decisions. |
| `pending_actions` | `agent` | Human-in-the-loop actions requiring member or admin confirmation. |
| `nutrition_plans` | `agent` | AI-generated personalized nutrition guidance and macro targets. |
| `weekly_plans` | `agent` | AI-generated personalized weekly workout schedules. |
| `coach_summaries` | `agent` | Weekly fitness progress summaries generated by AI coach. |
| `notification_log` | `notification` | Delivery status log for email and asynchronous messages. |

---

## 5. Domain Events

| Event Name | Payload Fields | Producer Module | Consumer Modules | Introduced In |
| :--- | :--- | :--- | :--- | :--- |
| `password.reset_requested` | `userId`, `token` | `auth` | `notification` (email dispatch) | Stage 2 |
| `user.deletion_requested` | `userId` | `user` | `booking`, `notification` | Stage 3 |
| `user.created` | `userId`, `role`, `tempPasswordDeliveredVia`, `tempPassword` | `admin` | `notification` (email dispatch) | Stage 3 |
| `user.suspended` | `userId` | `admin` | `notification` | Stage 3 |

---

## 6. REST Routes

All routes are prefixed with `/api/v1` and speak `application/json` (or `application/problem+json` for errors).

| Method | Path | Roles / Permissions | Request DTO | Response DTO | Status Codes | Stage |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Anonymous | `RegisterRequest` | `RegisterResponse` | 201, 400, 409 | Stage 2 |
| `POST` | `/api/v1/auth/login` | Anonymous | `LoginRequest` | `AuthResponse` | 200, 400, 401, 429 | Stage 2 |
| `POST` | `/api/v1/auth/refresh` | Anonymous | `RefreshRequest` | `AuthResponse` | 200, 400, 401 | Stage 2 |
| `POST` | `/api/v1/auth/logout` | Authenticated | `LogoutRequest` | Void | 204, 401 | Stage 2 |
| `POST` | `/api/v1/auth/change-password` | Authenticated | `ChangePasswordRequest` | Void | 204, 400, 401 | Stage 2 |
| `POST` | `/api/v1/auth/forgot-password` | Anonymous | `ForgotPasswordRequest` | `ForgotPasswordResponse` | 202, 400 | Stage 2 |
| `POST` | `/api/v1/auth/reset-password` | Anonymous | `ResetPasswordRequest` | Void | 204, 400 | Stage 2 |
| `GET` | `/api/v1/me/permissions` | Authenticated | None | `PermissionsResponse` | 200, 401 | Stage 2 |
| `GET` | `/api/v1/me` | Authenticated | None | `UserProfileResponse` | 200, 401 | Stage 3 |
| `PATCH` | `/api/v1/me` | Authenticated | `UpdateProfileRequest` | `UserProfileResponse` | 200, 400, 401, 409 | Stage 3 |
| `GET` | `/api/v1/me/export` | Authenticated | None | `UserDataExportResponse` | 200, 401 | Stage 3 |
| `POST` | `/api/v1/me/delete-request` | Authenticated | `DeleteAccountRequest` | Void | 202, 400, 401 | Stage 3 |
| `GET` | `/api/v1/admin/users` | `USER_READ` | Filters (q, role, status, page, size, sort) | `PageResponse<UserSummaryDto>` | 200, 401, 403 | Stage 3 |
| `GET` | `/api/v1/admin/users/{id}` | `USER_READ` | None | `AdminUserDetailResponse` | 200, 401, 403, 404 | Stage 3 |
| `POST` | `/api/v1/admin/users` | `USER_EDIT` (`SUPER_ADMIN` for Admin/Perms) | `AdminCreateUserRequest` | `UserSummaryDto` | 201, 400, 401, 403, 409 | Stage 3 |
| `PATCH` | `/api/v1/admin/users/{id}` | `USER_EDIT` | `AdminUpdateUserRequest` | `AdminUserDetailResponse` | 200, 400, 401, 403, 404, 409 | Stage 3 |
| `POST` | `/api/v1/admin/users/{id}/suspend` | `USER_EDIT` | None | `Map<String, String>` | 200, 401, 403, 404, 409 | Stage 3 |
| `POST` | `/api/v1/admin/users/{id}/reactivate`| `USER_EDIT` | None | `Map<String, String>` | 200, 401, 403, 404 | Stage 3 |
| `PUT` | `/api/v1/admin/users/{id}/permissions`| `SUPER_ADMIN` | `AdminPermissionsRequest` | `Map<String, Object>` | 200, 400, 401, 403, 404, 409 | Stage 3 |
| `GET` | `/api/v1/trainers` | Authenticated | None | `List<TrainerPublicResponse>` | 200, 401 | Stage 3 |
| `GET` | `/api/v1/trainers/{id}` | Authenticated | None | `TrainerPublicResponse` | 200, 401, 404 | Stage 3 |
| `GET` | `/api/v1/trainer/me` | Role `TRAINER` | None | `TrainerProfileResponse` | 200, 401, 403, 404 | Stage 3 |
| `PATCH` | `/api/v1/trainer/me` | Role `TRAINER` | `UpdateTrainerProfileRequest` | `TrainerProfileResponse` | 200, 400, 401, 403, 409 | Stage 3 |
| `PATCH` | `/api/v1/admin/trainers/{id}/rate` | `PAYOUT_APPROVE` or `SUPER_ADMIN` | `UpdateTrainerRateRequest` | `TrainerProfileResponse` | 200, 400, 401, 403, 404, 409 | Stage 3 |
| `GET` | `/api/v1/trainer/me/summary` | Role `TRAINER` | None | `TrainerSummaryResponse` | 200, 401, 403 | Stage 3 |
| `GET` | `/api/v1/admin/settings/ai` | `SETTINGS_EDIT` or `SUPER_ADMIN` | None | `AiSettingsResponse` | 200, 401, 403 | Stage 3 |
| `PUT` | `/api/v1/admin/settings/ai/key` | `SETTINGS_EDIT` or `SUPER_ADMIN` | `SetAiKeyRequest` | `AiSettingsResponse` | 200, 400, 401, 403 | Stage 3 |
| `DELETE`| `/api/v1/admin/settings/ai/key` | `SETTINGS_EDIT` or `SUPER_ADMIN` | None | Void | 204, 401, 403 | Stage 3 |
| `PUT` | `/api/v1/admin/settings/ai/model` | `SETTINGS_EDIT` or `SUPER_ADMIN` | `SetAiModelRequest` | `AiSettingsResponse` | 200, 400, 401, 403 | Stage 3 |
| `POST` | `/api/v1/admin/settings/ai/test` | `SETTINGS_EDIT` or `SUPER_ADMIN` | None | `AiTestResponse` | 200, 401, 403 | Stage 3 |
| `GET` | `/dev/mail` | Anonymous (Dev profile only) | None | `List<EmailMessageDto>` | 200 | Stage 1 |
| `DELETE` | `/dev/mail` | Anonymous (Dev profile only) | None | Void | 204 | Stage 1 |

---

## 7. Public Routes List (Single Source of Truth)

Defined centrally in `com.fitflow.security.PublicRoutes` and verified by `EndpointSecurityCoverageTest`:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `GET /actuator/health`
- `GET /api/v1/plans` (added in Stage 5)
- `GET /swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/swagger-resources/**` (dev/test only)
- `GET /dev/mail` (dev only)
- `* /error` (system error forward)

All other endpoints require authentication (deny by default).

---

## 8. Role & Permission Model

### Roles
- `MEMBER`: Regular fitness club member.
- `TRAINER`: Class coach / trainer.
- `ADMIN`: Administrator with granular permission flags.

### Admin Permissions
- `USER_READ`: View user profiles.
- `USER_EDIT`: Modify user details and permissions (Sensitive).
- `CLASS_EDIT`: Create and modify fitness classes.
- `PLAN_EDIT`: Modify membership plans.
- `REFUND_APPROVE`: Approve member payment refunds (Sensitive).
- `PAYOUT_APPROVE`: Approve trainer session payout batches (Sensitive).
- `SETTINGS_EDIT`: Modify system application settings (Sensitive).
- `SUPER_ADMIN`: Implies all permissions unconditionally.

*Sensitive Action Re-validation*: When checking sensitive permissions (`REFUND_APPROVE`, `PAYOUT_APPROVE`, `USER_EDIT`, `SETTINGS_EDIT`), `PermissionChecker` verifies token claims AND re-queries `admin_permissions` in the database to ensure immediate revocation takes effect.

---

## 9. Environment Variables

| Variable | Description | Default (Dev) | Required in Prod |
| :--- | :--- | :--- | :---: |
| `MYSQL_URL` | MySQL JDBC Connection URL | `jdbc:mysql://localhost:3306/fitflow...` | Yes |
| `MYSQL_USER` | MySQL Username | `fitflow_app` | Yes |
| `MYSQL_PASSWORD` | MySQL Password | `fitflow_pass` | Yes |
| `MYSQL_TEST_URL` | MySQL Test DB URL | `jdbc:mysql://localhost:3306/fitflow_test...` | No |
| `PG_URL` | PostgreSQL JDBC Connection URL | `jdbc:postgresql://localhost:5432/fitflow` | Yes |
| `PG_USER` | PostgreSQL Username | `fitflow_app` | Yes |
| `PG_PASSWORD` | PostgreSQL Password | `fitflow_pass` | Yes |
| `PG_TEST_URL` | PostgreSQL Test DB URL | `jdbc:postgresql://localhost:5432/fitflow_test` | No |
| `RABBIT_HOST` | RabbitMQ Host | `localhost` | No |
| `RABBIT_PORT` | RabbitMQ Port | `5672` | No |
| `MESSAGING_MODE` | Messaging Engine (`rabbit` or `inmemory`) | `rabbit` | Yes |
| `SMTP_HOST` | SMTP Mail Host | `localhost` | Yes |
| `SMTP_PORT` | SMTP Port | `3025` (GreenMail in dev) | Yes |
| `JWT_SECRET` | HMAC-SHA256 Signing Secret (>= 32 bytes) | Generated in `.local/jwt.secret` | Yes |
| `APP_MASTER_KEY` | AES-256-GCM 32-byte Base64 Encryption Key | Generated in `.local/master.key` | Yes |
| `GEMINI_API_KEY` | Google Gemini API Key | Stored in `app_secrets` or env | No (optional) |
| `CORS_ALLOWED_ORIGINS`| Permitted CORS Origins | `http://localhost:3000,http://localhost:5173` | Yes |

---

## 10. Stubs

| Stub Name | Location | Purpose | Test Proving Contract |
| :--- | :--- | :--- | :--- |
| `MockLlmClient` | `com.fitflow.agent.MockLlmClient` | Deterministic canned responses for AI agent workflows before Stage 8 Gemini integration. | Verified in unit and architecture tests. |
| `BookingCancellationStub` | `com.fitflow.booking.BookingCancellationStub` | Placeholder for future booking cancellation and quota restoration upon user deletion (Stage 4/5). | Verified via `BookingCancellationStubTest`. |
| `GeminiClientStub` | `com.fitflow.agent.GeminiClientStub` | Deterministic stub for live Gemini test connection before full LLM integration in Stage 8. | Verified via `GeminiClientStubTest`. |

---

## 11. Architectural & Engineering Decisions

1. **Java 17 LTS Baseline (Amended Rule R2)**:
   - *Decision*: Adopt Java 17 LTS as the compilation and runtime baseline, approved explicitly by the user during Stage 1 preflight.
   - *Rationale*: Spring Boot 3.5.3 fully supports Java 17 LTS. All required modern features (Records, Sealed classes, Pattern matching for instanceof, Text blocks) are 100% supported in Java 17 without requiring changes to the user's host machine JDK installation.
2. **JJWT Chosen for JWT Token Handling**:
   - *Decision*: Standardized on `io.jsonwebtoken:jjwt` (0.12.6) for tokens.
   - *Rationale*: Clean modular split (`api`, `impl`, `jackson`), robust secret key validation, and first-class Java 17 support.
3. **Dual DataSource Isolation**:
   - *Decision*: Separate entity packages and explicit `LocalContainerEntityManagerFactoryBean` definitions for MySQL and PostgreSQL. Excluded default Spring Boot DataSource auto-configurations to prevent ambiguous primary bean conflicts.
   - *Rationale*: Completely decouples transactional relational workflows (MySQL) from agent conversation memory and audit storage (PostgreSQL).
4. **Secrets Encryption at Rest**:
   - *Decision*: AES-256-GCM with version byte prefix + random 12-byte IV + ciphertext + 128-bit authentication tag.
   - *Rationale*: Provides authenticated encryption (AEAD). Any tampering with ciphertext or wrong decryption key throws an immediate authentication failure.
5. **No Entity Leakage (ArchUnit Gate)**:
   - *Decision*: Strict ArchUnit automated rules enforce that `@RestController` classes never depend on `@Entity` or `repository` packages, and never accept or return entities.
6. **Password Hashing Baseline (BCrypt 12)**:
   - *Decision*: Standardized on BCrypt with work factor (strength) 12 for all password hashing.
   - *Rationale*: Work factor 12 provides strong protection against GPU dictionary and brute-force attacks while maintaining acceptable verification latency (< 250ms per check).
7. **Refresh Token Security: Rotation & Family Revocation**:
   - *Decision*: Refresh tokens are single-use rotated, stored only as SHA-256 hashes (`token_hash CHAR(64)`), and grouped by a UUID `family_id`.
   - *Rationale*: Replaying a revoked refresh token immediately revokes all tokens within that family, cutting off compromised sessions across all devices. Raw tokens never reside in persistent storage.
8. **Rate Limiting & Brute-Force Lockout**:
   - *Decision*: In-memory token bucket rate limiting (10 attempts/min per IP+email via Bucket4j) combined with progressive lockout (5 failed login attempts locks account for 15 minutes, returning 429 with `Retry-After` header).
   - *Rationale*: Thwarts distributed credential stuffing and automated password guessing while giving locked users a deterministic retry window.
9. **Automated Endpoint Security Coverage Gate (`EndpointSecurityCoverageTest`)**:
   - *Decision*: Every endpoint mapped in Spring MVC is audited at test time to verify it is explicitly registered in `PublicRoutes` or guarded with `@PreAuthorize`.
   - *Rationale*: Guarantees deny-by-default security across all future stages; any endpoint accidentally left open fails the test gate immediately.
10. **Acyclic Module Boundaries via Dependency Inversion Principle (DIP)**:
    - *Decision*: When `user` requires token revocation on account deletion, `user` defines `RefreshTokenRevoker` interface implemented by `RefreshTokenService` in `auth`. When `settings` requires AI connection test execution, `settings` defines `AiConnectionTester` interface implemented by `GeminiClientStub` in `agent`.
    - *Rationale*: Strictly preserves ArchUnit Rule D (acyclic package slice graph `com.fitflow.(*)..`) without circular dependencies between domain modules.
11. **Administrative Trainer Compensation Segregation**:
    - *Decision*: Trainer profile self-service (`/api/v1/trainer/me`) and public trainer catalogs (`/api/v1/trainers`) reside in `com.fitflow.trainer`, while administrative session pay rate mutations (`/api/v1/admin/trainers/{id}/rate`) and audit logging reside in `com.fitflow.admin`.
    - *Rationale*: Prevents `trainer` from depending on `admin`, maintaining a clean unidirectional dependency `admin` -> `trainer`.
12. **Trainer Availability Slot Validation Engine**:
    - *Decision*: Dedicated `TrainerAvailabilityValidator` validates day of week (1..7), enforces 24-hour time formats `HH:mm`, guarantees `end` strictly after `start`, and prevents overlapping time intervals for the same weekday.
    - *Rationale*: Ensures robust scheduling data integrity before class session calendar scheduling is introduced in Stage 4.
13. **Last SUPER_ADMIN Protective Guard**:
    - *Decision*: Suspending a user or updating an admin's permissions queries the count of active super administrators in the database; if the target holds `SUPER_ADMIN` and active count is <= 1, the operation is blocked with 409 Conflict. Additionally, administrators cannot suspend their own accounts.
    - *Rationale*: Prevents accidental system-wide lockout of all super administrative access.
14. **Dynamic AI Key & Model Configuration with Safe Redaction**:
    - *Decision*: AI API keys are validated (`^AIza[^\s]{16,196}$`), encrypted via `SecretsService` (AES-256-GCM), and masked on all reads (`maskedLast4`). Audit logs record `configured: true/false` without secret keys. `LogRedactor` ensures keys, passwords, and tokens are redacted to `[REDACTED]` in all application logging.
    - *Rationale*: Provides flexible runtime configuration for multi-agent capabilities while enforcing zero secret leakage in logs, audit trails, and client responses (Rule R10).

---

## 12. Assumptions

1. **Currency**: All money amounts are stored as `BIGINT` representing paise (INR). No floating point arithmetic is permitted for monetary calculations (Rule R4).
2. **Time**: All dates and timestamps are stored in UTC using `java.time.Instant`. Time is always accessed via the injected `java.time.Clock` bean (Rule R5).
3. **Display Timezone**: Documented as `Asia/Kolkata` (`fitflow.display-timezone`), used solely for user-facing UI formatting; internal persistence is strictly UTC.
4. **Dual Database Consistency**: MySQL and PostgreSQL are eventually consistent via the transactional Outbox Pattern (`outbox_events`). Distributed 2PC (XA) transactions are explicitly avoided.
5. **Gemini API Key**: The Gemini LLM key is entered at runtime by an administrator into `app_secrets` (encrypted at rest), with an optional fallback to `GEMINI_API_KEY` in environment variables.

---

## 13. Stage Log

| Stage | Date | Built | Verified How | UNVERIFIED Items | Known Gaps |
| :---: | :---: | :--- | :--- | :--- | :--- |
| **Stage 1** | 2026-10-27 | Repository scaffold, Maven build with Java 17, Dual MySQL/PostgreSQL DataSources & Flyway migrations, AES-256-GCM EncryptionService, SecretsService, ErrorCode & GlobalExceptionHandler, CorrelationIdFilter, PageRequestDto, ArchUnit 7 architecture rules, GreenMail dev mail controller, DevSeedRunner. | `mvn verify` green (35 tests: 25 unit + 10 IT); Jacoco coverage check passed; Live health check UP; 3 Sanity-Breaks executed and proven. | None | Authentication workflows (JWT issuance, login endpoints) scheduled for Stage 2. |
| **Stage 2** | 2026-10-28 | Production-grade JWT auth (`TokenService`, JJWT), BCrypt(12), role & permission model (`PermissionChecker`), token rotation with family revocation on reuse, `PasswordPolicy`, forgot/reset password with outbox events, rate limiting & lockout (`LoginAttemptService`, Bucket4j), `AdminActionLogger`, `AgeService`, `EndpointSecurityCoverageTest` automated gate. | `mvn verify` green (90 tests: 51 unit + 39 IT); Jacoco coverage check passed; EndpointSecurityCoverageTest passed; Live app walkthrough (10 endpoints) passed; 3 Sanity-Breaks executed and proven. | None | User, trainer, and admin management scheduled for Stage 3. |
| **Stage 3** | 2026-10-28 | Member self-service (`GET/PATCH /api/v1/me`, `GET /export`, `POST /delete-request`), Admin user management with pagination, filtering & last-super-admin guard (`/api/v1/admin/users`), Trainer catalog & self-service availability validation (`/api/v1/trainers`, `/api/v1/trainer/me`, `/rate`), AI runtime settings with AES-256-GCM encrypted Gemini key & mock fallback (`/api/v1/admin/settings/ai`), `BookingCancellationStub`, `GeminiClientStub`. | `mvn verify` green (160 tests: 66 unit + 94 IT); Jacoco coverage check passed; EndpointSecurityCoverageTest passed; ArchUnit 7 gates passed; Live app walkthrough (21 endpoints) passed; 3 Sanity-Breaks executed and proven. | None | Fitness class catalog, room scheduling, and booking engine scheduled for Stage 4. |
