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
| `AGE_RESTRICTED` | 403 Forbidden | User does not meet minimum age requirement for class or plan. |
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
| *(None yet)* | — | — | — | Stage 1 (Schema scaffolded) |

---

## 6. REST Routes

All routes are prefixed with `/api/v1` and speak `application/json` (or `application/problem+json` for errors).

| Method | Path | Roles / Permissions | Request DTO | Response DTO | Status Codes | Stage |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/dev/mail` | Anonymous (Dev profile only) | None | `List<EmailMessageDto>` | 200 | Stage 1 |
| `DELETE` | `/dev/mail` | Anonymous (Dev profile only) | None | Void | 204 | Stage 1 |

---

## 7. Environment Variables

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
| `JWT_SECRET` | HMAC-SHA256 Signing Secret (>= 32 bytes) | Generated / dev key | Yes |
| `APP_MASTER_KEY` | AES-256-GCM 32-byte Base64 Encryption Key | Generated in `.local/master.key` | Yes |
| `GEMINI_API_KEY` | Google Gemini API Key | Stored in `app_secrets` or env | No (optional) |
| `CORS_ALLOWED_ORIGINS`| Permitted CORS Origins | `http://localhost:3000,http://localhost:5173` | Yes |

---

## 8. Stubs

| Stub Name | Location | Purpose | Test Proving Contract |
| :--- | :--- | :--- | :--- |
| `MockLlmClient` | `com.fitflow.agent.MockLlmClient` | Deterministic canned responses for AI agent workflows before Stage 8 Gemini integration. | Verified in unit and architecture tests. |

---

## 9. Architectural & Engineering Decisions

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

---

## 10. Assumptions

1. **Currency**: All money amounts are stored as `BIGINT` representing paise (INR). No floating point arithmetic is permitted for monetary calculations (Rule R4).
2. **Time**: All dates and timestamps are stored in UTC using `java.time.Instant`. Time is always accessed via the injected `java.time.Clock` bean (Rule R5).
3. **Display Timezone**: Documented as `Asia/Kolkata` (`fitflow.display-timezone`), used solely for user-facing UI formatting; internal persistence is strictly UTC.
4. **Dual Database Consistency**: MySQL and PostgreSQL are eventually consistent via the transactional Outbox Pattern (`outbox_events`). Distributed 2PC (XA) transactions are explicitly avoided.
5. **Gemini API Key**: The Gemini LLM key is entered at runtime by an administrator into `app_secrets` (encrypted at rest), with an optional fallback to `GEMINI_API_KEY` in environment variables.

---

## 11. Stage Log

| Stage | Date | Built | Verified How | UNVERIFIED Items | Known Gaps |
| :---: | :---: | :--- | :--- | :--- | :--- |
| **Stage 1** | 2026-10-27 | Repository scaffold, Maven build with Java 17, Dual MySQL/PostgreSQL DataSources & Flyway migrations, AES-256-GCM EncryptionService, SecretsService, ErrorCode & GlobalExceptionHandler, CorrelationIdFilter, PageRequestDto, ArchUnit 7 architecture rules, GreenMail dev mail controller, DevSeedRunner. | `mvn verify` green (35 tests: 25 unit + 10 IT); Jacoco coverage check passed; Live health check UP; 3 Sanity-Breaks executed and proven. | None | Authentication workflows (JWT issuance, login endpoints) scheduled for Stage 2. |
