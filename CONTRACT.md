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
| **WireMock Standalone** | `org.wiremock:wiremock-standalone` | `3.12.0` | HTTP service virtualization for Razorpay gateway integration tests |
| **Thymeleaf Spring Boot** | `org.springframework.boot:spring-boot-starter-thymeleaf` | `3.5.3` | Dual HTML and plain-text email notification rendering |
| **Spring AMQP / RabbitMQ**| `org.springframework.boot:spring-boot-starter-amqp` | `3.5.3` | RabbitMQ topic exchange topology, outbox relay publisher confirms, and listener containers |

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
| `idempotency_records` | `booking` | No | SHA-256 request payload hash, cached response JSON, status code, and expiry for idempotent mutations. |
| `membership_plans` | `membership` | Yes (`version`) | Plan catalog, prices in paise, session quotas, free tier flags. |
| `memberships` | `membership` | Yes (`version`) | User plan subscriptions, quotas used, validity windows. |
| `membership_changes` | `membership` | No | Proration audit log for plan upgrades, downgrades, and refunds. |
| `pending_membership_changes` | `membership` | Yes (`version`) | Two-phase pending subscription/change intents awaiting payment confirmation. |
| `payments` | `payment` | No | Monetary transactions, gateway references, idempotency keys. |
| `refunds` | `payment` | No | Refund requests, approval states, idempotency keys. |
| `refund_allocations` | `payment` | No | FIFO allocations of refunds across original payment intents. |
| `payment_webhook_events` | `payment` | No | Idempotent log of processed payment gateway webhook payloads. |
| `invoices` | `payment` | No | Tax-compliant invoice numbers and total amounts in paise. |
| `trainer_payouts` | `payout` | Yes (`version`) | Payout batches for trainers across date periods. |
| `trainer_session_earnings`| `payout` | No | Earnings per session linked to payout batches. |
| `outbox_events` | `notification`| No | Transactional outbox events for guaranteed message delivery with encrypted secret payload scrubbing. |
| `processed_events` | `notification`| No | Deduplication log for idempotent event handlers (`consumer`, `event_id`, `processed_at`). |
| `booking_reminders` | `booking` | No | Deduplication tracking table for 24h session reminder jobs (`booking_id`, `session_id`, `reminded_at`). |
| `membership_notices`| `membership` | No | Deduplication tracking table for membership expiry and auto-renew notices (`membership_id`, `notice_type`, `sent_at`). |
| `job_locks` | `common` | No | Advisory distributed row-based lock for background cron jobs. |
| `member_health_profiles`| `wellness` | Yes (`version`) | Encrypted JSON blob containing sensitive member health data. |
| `health_consents` | `wellness` | No | Audit trail of health data consents granted or revoked. |
| `body_metrics` | `wellness` | No | Encrypted biometric measurements (weight, height). |
| `progress_logs` | `wellness` | No | Encrypted workout and wellness journal entries. |
| `class_feedback` | `wellness` | No | Post-session ratings, pain detection, and encrypted comments. |
| `admin_action_log` | `admin` | No | Audit log of all sensitive admin actions with before/after diffs. |
| `free_session_grants` | `membership` | No | Audit log of free session grants awarded via retention offers or promotions. |
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
| `job_runs` | `agent` | Execution history, duration, and processed/failed counts for agent background jobs. |
| `at_risk_flags` | `agent` | Records of churn-risk member detections for deduplication and proactive retention outreach. |
| `notification_log` | `notification` | Delivery status log for email and asynchronous messages (`event_id`, `recipient_user_id`, `template`, `category`, `status`). |

---

## 5. Domain Events & Messaging Topology

### Domain Events Registry

| Event Name | Plain Payload Fields | Encrypted Secret Fields (`payload_secret`) | Producer Module | Consumer Modules | Introduced In |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `password.reset_requested` | `userId` | `token` (AES-256-GCM) | `auth` | `notification` (email dispatch) | Stage 2 (scrubbed in 6) |
| `user.deletion_requested` | `userId` | None | `user` | `booking`, `notification` | Stage 3 |
| `user.created` | `userId`, `role`, `tempPasswordDeliveredVia` | `tempPassword` (AES-256-GCM) | `admin` | `notification` (email dispatch) | Stage 3 (scrubbed in 6) |
| `user.suspended` | `userId` | None | `admin` | `notification` | Stage 3 |
| `session.rescheduled` | `sessionId`, `previousStartsAt`, `newStartsAt`, `className` | None | `session` | `booking`, `notification` | Stage 4 |
| `session.cancelled` | `sessionId`, `reason`, `className`, `sessionStartTime` | None | `session` | `booking`, `notification` | Stage 4 |
| `session.reminder` | `bookingId`, `sessionId`, `memberId`, `startsAt`, `className` | None | `booking` (`SessionReminderJob`) | `notification` | Stage 6 |
| `booking.created` | `bookingId`, `sessionId`, `memberId`, `userId`, `className`, `trainerName`, `sessionStartTime` | None | `booking` | `notification` | Stage 4 |
| `booking.cancelled` | `bookingId`, `sessionId`, `memberId`, `userId`, `reason`, `className`, `sessionStartTime` | None | `booking` | `notification` | Stage 4 |
| `waitlist.promoted` | `waitlistEntryId`, `sessionId`, `memberId`, `userId`, `bookingId`, `className`, `sessionStartTime` | None | `booking` | `notification` | Stage 4 |
| `attendance.marked` | `sessionId`, `bookingId`, `memberId`, `status` | None | `booking` | `notification`, `payout` | Stage 4 |
| `session.completed` | `sessionId`, `trainerId`, `attendeeCount`, `amountPaise` | None | `booking` | `payout`, `notification` | Stage 4 |
| `membership.started` | `membershipId`, `memberId`, `userId`, `planCode`, `planName`, `endsAt` | None | `membership` | `notification` | Stage 5 |
| `membership.changed` | `membershipId`, `memberId`, `userId`, `fromPlanCode`, `toPlanCode`, `amountDuePaise`, `refundPaise` | None | `membership` | `notification` | Stage 5 |
| `membership.expiring` | `membershipId`, `memberId`, `userId`, `planCode`, `planName`, `endsAt` | None | `membership` (`MembershipExpiringJob`)| `notification` | Stage 6 |
| `membership.expired` | `membershipId`, `memberId`, `userId`, `planCode`, `planName` | None | `membership` (`MembershipExpiringJob`)| `notification` | Stage 6 |
| `membership.cancelled` | `membershipId`, `memberId`, `userId`, `planCode`, `refundPaise` | None | `membership` | `notification` | Stage 5 |
| `payment.intent_created` | `paymentId`, `gatewayOrderId`, `memberId`, `amountPaise`, `gateway` | None | `payment` | `notification` | Stage 5 |
| `payment.succeeded` | `paymentId`, `gatewayPaymentId`, `memberId`, `userId`, `amountPaise`, `invoiceNumber` | None | `payment` | `membership`, `notification` | Stage 5 |
| `payment.failed` | `paymentId`, `memberId`, `userId`, `amountPaise`, `failureReason` | None | `payment` | `notification` | Stage 5 |
| `refund.pending_approval`| `refundId`, `paymentId`, `memberId`, `amountPaise` | None | `payment` | `notification` | Stage 6 |
| `refund.issued` | `refundId`, `paymentId`, `memberId`, `userId`, `amountPaise` | None | `payment` | `notification` | Stage 6 |
| `payout.approved` | `payoutBatchId`, `approvedBy`, `trainerUserId`, `totalPaise` | None | `payout` | `notification` | Stage 5 |
| `payout.paid` | `payoutBatchId`, `markedPaidBy`, `totalPaise` | None | `payout` | `notification` | Stage 5 |
| `feedback.pain_reported` | `sessionId`, `memberId`, `trainerUserId`, `className`, `painSeverity`, `painLocation` | None | `wellness` | `notification`, `agent` | Stage 6 |
| `member.at_risk` | `memberId`, `daysInactive`, `lastAttendedAt` | None | `booking` | `agent` | Stage 6 |
| `weekly.summary` | `memberId`, `sessionsAttended`, `caloriesBurned` | None | `booking` | `agent` | Stage 6 |
| `profile.completed` | `memberId`, `completedAt` | None | `wellness` | `agent` | Stage 6 |

### Sensitive Payload Fields & Secret Scrubbing
- **Secret Encryption**: `OutboxService.appendWithSecret(aggregateType, aggregateId, eventType, payloadMap, secretMap)` serializes `secretMap` to JSON and encrypts it with AES-256-GCM into MySQL column `payload_secret`.
- **Forbidden Key Enforcement**: `OutboxService.append` throws `IllegalArgumentException` if `payloadMap` contains keys: `password`, `token`, `tempPassword`, `secret`, `apiKey`.
- **Envelope Delivery**: The relay bundles ciphertext into `EventEnvelope.enc`. Only authorized consumers decrypt it with `EncryptionService`.
- **Scrubbing Guarantee**: When an outbox row transitions to `SENT`, `payload_secret` is immediately updated to `NULL` in the database.

### Notification Routing Table

| Event Name | Target Recipient | Thymeleaf Template | Preference Category | Gating Logic |
| :--- | :--- | :--- | :--- | :--- |
| `user.created` | New User Email | `welcome-created` | `TRANSACTIONAL` | Mandatory send |
| `password.reset_requested` | User Email | `password-reset` | `TRANSACTIONAL` | Mandatory send |
| `user.suspended` | User Email | `account-suspended` | `TRANSACTIONAL` | Mandatory send |
| `booking.created` | Member Email | `booking-confirmed` | `BOOKINGS` | Gated by `emailBookings` |
| `booking.cancelled` | Member Email | `booking-cancelled` | `BOOKINGS` | Gated by `emailBookings` |
| `waitlist.promoted` | Member Email | `waitlist-promoted` | `BOOKINGS` | Gated by `emailBookings` |
| `session.cancelled` | All Confirmed Attendees | `session-cancelled` | `BOOKINGS` | Gated by `emailBookings` |
| `session.rescheduled` | All Confirmed Attendees | `session-rescheduled` | `BOOKINGS` | Gated by `emailBookings` |
| `session.reminder` | Member Email | `session-reminder` | `REMINDERS` | Gated by `emailReminders` |
| `membership.started` | Member Email | `membership-started` | `TRANSACTIONAL` | Mandatory send |
| `membership.changed` | Member Email | `membership-changed` | `TRANSACTIONAL` | Mandatory send |
| `membership.expiring` | Member Email | `membership-expiring` | `REMINDERS` | Gated by `emailReminders` |
| `membership.expired` | Member Email | `membership-expired` | `REMINDERS` | Gated by `emailReminders` |
| `payment.succeeded` | Member Email | `payment-receipt` | `TRANSACTIONAL` | Mandatory send |
| `payment.failed` | Member Email | `payment-failed` | `TRANSACTIONAL` | Mandatory send |
| `refund.pending_approval`| `REFUND_APPROVE` Admins | `refund-needs-approval`| `TRANSACTIONAL` | Mandatory send |
| `refund.issued` | Member Email | `refund-issued` | `TRANSACTIONAL` | Mandatory send |
| `payout.approved` | Trainer User Email | `payout-approved` | `TRANSACTIONAL` | Mandatory send |
| `feedback.pain_reported` | Trainer User Email | `trainer-pain-flag` | `TRANSACTIONAL` | Mandatory send |
| `consent.granted` | Internal Event | None | `INTERNAL` | Bound to `q.agent.events` |
| `consent.revoked` | Internal Event | None | `INTERNAL` | Bound to `q.agent.events` |
| `profile.completed` | Internal Event | None | `INTERNAL` | Bound to `q.agent.events` |
| `profile.updated` | Internal Event | None | `INTERNAL` | Bound to `q.agent.events` |
| `health.data_deleted` | Internal Event | None | `INTERNAL` | Bound to `q.agent.events` |

*Rate Limiting*: 20 emails/hour sliding window limit per recipient. Excessive emails are dropped with a WARN log containing the `userId` (never the email address).
*Formatting*: Dual HTML (`.html`) and plain-text (`.txt`) parts rendered via Thymeleaf with strict XML escaping and zero health data.

### Messaging Topology & Delivery Guarantees
- **Exchange Topology**:
  - Primary Exchange: `fitflow.events` (Topic, Durable)
  - Dead Letter Exchange: `fitflow.events.dlx` (Topic, Durable)
- **Queues & Routing**:
  - `q.notifications.email` bound to routing keys for notification table events; dead-letters to `q.notifications.email.dlq`.
  - `q.agent.events` bound to `member.at_risk`, `weekly.summary`, `feedback.pain_reported`, `profile.completed`, `booking.created`, `attendance.marked`; dead-letters to `q.agent.events.dlq`.
- **Consumer Retry & Dead Lettering**:
  - Retries 3 times with backoff (`1s`, `4s`, `16s`). On 4th failure, message is rejected without requeue, landing in the respective DLQ.
  - Poison messages (malformed JSON, unknown event type) are rejected immediately to the DLQ.
- **Delivery Guarantees**:
  - Delivery is **at-least-once** and **unordered** across event types.
  - All consumers **MUST be idempotent**. Email notifications track deduplication against PostgreSQL `notification_log` (`hasAlreadySent`). Internal event consumers write to `processed_events`.

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
| `GET` | `/api/v1/classes/metadata` | Authenticated | None | `ClassMetadataResponse` | 200, 401 | Stage 4 |
| `GET` | `/api/v1/classes` | Authenticated | Filters (category, intensity, impactLevel, q, page, size) | `PageResponse<ClassSummaryDto>` | 200, 401 | Stage 4 |
| `GET` | `/api/v1/classes/{id}` | Authenticated | None | `ClassDetailResponse` | 200, 401, 404 | Stage 4 |
| `POST` | `/api/v1/admin/classes` | `CLASS_EDIT` | `CreateClassRequest` | `ClassDetailResponse` | 201, 400, 401, 403 | Stage 4 |
| `PUT` | `/api/v1/admin/classes/{id}` | `CLASS_EDIT` | `UpdateClassRequest` | `ClassDetailResponse` | 200, 400, 401, 403, 404, 409 | Stage 4 |
| `POST` | `/api/v1/admin/classes/{id}/deactivate` | `CLASS_EDIT` | None | `ClassDetailResponse` | 200, 401, 403, 404 | Stage 4 |
| `POST` | `/api/v1/admin/classes/{id}/activate` | `CLASS_EDIT` | None | `ClassDetailResponse` | 200, 401, 403, 404 | Stage 4 |
| `POST` | `/api/v1/admin/classes/{id}/confirm-metadata` | `CLASS_EDIT` | None | `ClassDetailResponse` | 200, 401, 403, 404 | Stage 4 |
| `POST` | `/api/v1/admin/sessions` | `CLASS_EDIT` | `CreateSessionRequest` | `List<SessionDetailResponse>` | 201, 400, 401, 403, 404, 409 | Stage 4 |
| `PATCH` | `/api/v1/admin/sessions/{id}/reschedule` | `CLASS_EDIT` | `RescheduleSessionRequest` | `SessionDetailResponse` | 200, 400, 401, 403, 404, 409 | Stage 4 |
| `POST` | `/api/v1/admin/sessions/{id}/cancel` | `CLASS_EDIT` | `CancelSessionRequest` | `SessionDetailResponse` | 200, 400, 401, 403, 404, 409 | Stage 4 |
| `GET` | `/api/v1/sessions` | Authenticated | Filters (from, to, classId, trainerId, room) | `List<SessionSummaryDto>` | 200, 400, 401 | Stage 4 |
| `GET` | `/api/v1/sessions/{id}` | Authenticated | None | `SessionDetailResponse` | 200, 401, 404 | Stage 4 |
| `GET` | `/api/v1/trainer/sessions` | Role `TRAINER` | Filters (from, to) | `List<SessionSummaryDto>` | 200, 400, 401, 403 | Stage 4 |
| `GET` | `/api/v1/admin/reports/utilization` | `USER_READ` or `SUPER_ADMIN` | Filters (from, to) | `List<SessionUtilizationReportDto>` | 200, 400, 401, 403 | Stage 4 |
| `POST` | `/api/v1/bookings` | Role `MEMBER` | `CreateBookingRequest` | `BookingResponse` (201) / `WaitlistResponse` (202) | 201, 202, 400, 401, 402, 403, 404, 409, 422 | Stage 4 |
| `DELETE`| `/api/v1/bookings/{id}` | Role `MEMBER` | None | Void | 204, 401, 403, 404, 409 | Stage 4 |
| `GET` | `/api/v1/me/bookings` | Role `MEMBER` | None | `List<BookingResponse>` | 200, 401, 403 | Stage 4 |
| `POST` | `/api/v1/admin/bookings/{id}/cancel` | `USER_EDIT` or `SUPER_ADMIN` | None | Void | 204, 401, 403, 404, 409 | Stage 4 |
| `DELETE`| `/api/v1/waitlist/{sessionId}` | Role `MEMBER` | None | Void | 204, 401, 403, 404 | Stage 4 |
| `POST` | `/api/v1/trainer/sessions/{id}/attendance` | Role `TRAINER` | `MarkAttendanceRequest` | Void | 200, 400, 401, 403, 404, 409 | Stage 4 |
| `POST` | `/api/v1/trainer/sessions/{id}/complete` | Role `TRAINER` | `CompleteSessionRequest` | Void | 200, 400, 401, 403, 404, 409 | Stage 4 |
| `GET` | `/api/v1/plans` | Anonymous | None | `List<PlanDto>` | 200 | Stage 5 |
| `POST` | `/api/v1/admin/plans` | `PLAN_EDIT` | `CreatePlanRequest` | `PlanDto` | 201, 400, 401, 403, 409 | Stage 5 |
| `PUT` | `/api/v1/admin/plans/{id}` | `PLAN_EDIT` | `UpdatePlanRequest` | `PlanDto` | 200, 400, 401, 403, 404, 409 | Stage 5 |
| `GET` | `/api/v1/me/membership` | Authenticated | None | `MemberMembershipSummaryResponse` | 200, 401 | Stage 5 |
| `POST` | `/api/v1/me/membership/change-preview` | Authenticated | `ChangePreviewRequest` | `ChangePreviewResponse` | 200, 400, 401, 409 | Stage 5 |
| `POST` | `/api/v1/memberships/subscribe` | Authenticated | `SubscribeRequest` | `PaymentIntentResponse` | 202, 400, 401, 409 | Stage 5 |
| `POST` | `/api/v1/memberships/change` | Authenticated | `ChangeMembershipRequest` | `PaymentIntentResponse` / `MemberMembershipSummaryResponse` | 200, 202, 400, 401, 409 | Stage 5 |
| `POST` | `/api/v1/memberships/cancel` | Authenticated | None | `Map<String, Object>` | 200, 401, 409 | Stage 5 |
| `POST` | `/api/v1/payments/{paymentId}/confirm` | Role `MEMBER` | `ConfirmPaymentRequest` | `PaymentDetailResponse` | 200, 202, 400, 401, 404 | Stage 5 |
| `GET` | `/api/v1/payments/{paymentId}` | Role `MEMBER` | None | `PaymentDetailResponse` | 200, 401, 404 | Stage 5 |
| `POST` | `/api/v1/admin/refunds/{refundId}/approve` | `REFUND_APPROVE` | None | `RefundDetailResponse` | 200, 401, 403, 404, 409 | Stage 5 |
| `POST` | `/api/v1/admin/refunds/{refundId}/reject` | `REFUND_APPROVE` | None | `RefundDetailResponse` | 200, 401, 403, 404, 409 | Stage 5 |
| `GET` | `/api/v1/trainer/me/earnings` | Role `TRAINER` | None | `TrainerEarningsResponse` | 200, 401, 403 | Stage 5 |
| `POST` | `/api/v1/admin/payouts` | `PAYOUT_MANAGE` or `ADMIN` | `CreatePayoutBatchRequest` | `PayoutBatchResponse` | 201, 400, 401, 403 | Stage 5 |
| `GET` | `/api/v1/admin/payouts/{id}` | `PAYOUT_MANAGE` or `ADMIN` | None | `PayoutBatchResponse` | 200, 401, 403, 404 | Stage 5 |
| `POST` | `/api/v1/admin/payouts/{id}/approve` | `PAYOUT_MANAGE` or `ADMIN` | None | `PayoutBatchResponse` | 200, 401, 403, 404, 409 | Stage 5 |
| `POST` | `/api/v1/admin/payouts/{id}/mark-paid` | `PAYOUT_MANAGE` or `ADMIN` | None | `PayoutBatchResponse` | 200, 401, 403, 404, 409 | Stage 5 |
| `GET` | `/api/v1/admin/payouts/{id}/export.csv` | `PAYOUT_MANAGE` or `ADMIN` | None | `text/csv` | 200, 401, 403, 404 | Stage 5 |
| `GET` | `/api/v1/admin/messaging/outbox` | `SETTINGS_EDIT` or `SUPER_ADMIN` | Filters (status, page, size) | `PageResponse<OutboxEventSummaryResponse>` | 200, 401, 403 | Stage 6 |
| `POST` | `/api/v1/admin/messaging/outbox/{id}/retry` | `SETTINGS_EDIT` or `SUPER_ADMIN` | None | `OutboxEventSummaryResponse` | 200, 401, 403, 404, 409 | Stage 6 |
| `GET` | `/api/v1/admin/messaging/queues` | `SETTINGS_EDIT` or `SUPER_ADMIN` | None | `QueueDepthResponse` | 200, 401, 403 | Stage 6 |
| `POST` | `/api/v1/admin/messaging/dlq/{queue}/replay` | `SUPER_ADMIN` | Query param `limit` (max 100) | `DlqReplayResponse` | 200, 400, 401, 403, 404 | Stage 6 |
| `GET` | `/api/v1/wellness/consent` | Role `MEMBER` | None | `ConsentInfoResponse` | 200, 401, 403 | Stage 7 |
| `POST` | `/api/v1/wellness/consent` | Role `MEMBER` | `GrantConsentRequest` | Void | 200, 400, 401, 403, 409 | Stage 7 |
| `DELETE` | `/api/v1/wellness/consent/{purpose}` | Role `MEMBER` | None | Void | 204, 400, 401, 403 | Stage 7 |
| `GET` | `/api/v1/wellness/metadata` | Authenticated | None | `WellnessMetadataResponse` | 200, 401 | Stage 7 |
| `GET` | `/api/v1/wellness/profile` | Role `MEMBER` | None | `ProfileResponse` | 200, 401, 403, 404 | Stage 7 |
| `PUT` | `/api/v1/wellness/profile` | Role `MEMBER` | `FitnessProfile` | `ProfileResponse` | 200, 400, 401, 403 | Stage 7 |
| `POST` | `/api/v1/wellness/metrics` | Role `MEMBER` | `BodyMetricRequest` | `BodyMetricResponse` | 201, 400, 401, 403 | Stage 7 |
| `GET` | `/api/v1/wellness/metrics` | Role `MEMBER` | Filters (from, to) | `List<BodyMetricResponse>` | 200, 400, 401, 403 | Stage 7 |
| `GET` | `/api/v1/wellness/export` | Role `MEMBER` | None | `HealthExportResponse` | 200, 401, 403 | Stage 7 |
| `DELETE` | `/api/v1/wellness/data` | Role `MEMBER` | `DeleteHealthDataRequest` | Void | 200, 400, 401, 403 | Stage 7 |
| `GET` | `/api/v1/wellness/classes/safe` | Role `MEMBER` | Filters (from, to, page, size) | `SafeClassesResponse` | 200, 400, 401, 403 | Stage 7 |
| `GET` | `/api/v1/wellness/classes/{classId}/safety` | Role `MEMBER` | None | `SafetyVerdict` | 200, 401, 403, 404 | Stage 7 |
| `GET` | `/api/v1/wellness/nutrition/numbers` | Role `MEMBER` | None | `NutritionNumbersResponse` | 200, 401, 403 | Stage 7 |
| `GET` | `/api/v1/wellness/classes/{classId}/burn` | Role `MEMBER` | None | `ClassBurnResponse` | 200, 400, 401, 403, 404 | Stage 7 |
| `GET` | `/api/v1/wellness/progress/weekly` | Role `MEMBER` | Filters (weekStart) | `WeeklyProgressResponse` | 200, 400, 401, 403 | Stage 7 |
| `POST` | `/api/v1/bookings/{bookingId}/feedback` | Role `MEMBER` | `ClassFeedbackRequest` | `FeedbackResponse` | 200, 400, 401, 403, 404, 409 | Stage 7 |
| `GET` | `/api/v1/wellness/proposals` | Role `MEMBER` | None | `List<RestrictionProposalDto>` | 200, 401, 403 | Stage 7 |
| `POST` | `/api/v1/wellness/proposals/{id}/accept` | Role `MEMBER` | `AcceptProposalRequest` | Void | 200, 400, 401, 403, 404 | Stage 7 |
| `POST` | `/api/v1/wellness/proposals/{id}/dismiss` | Role `MEMBER` | None | Void | 200, 401, 403, 404 | Stage 7 |
| `GET` | `/api/v1/trainer/sessions/{id}/prep` | Role `TRAINER` | None | `TrainerPrepResponse` | 200, 401, 403, 404 | Stage 7 |
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
- `POST /api/v1/payments/webhooks/razorpay` (added in Stage 5B)
- `GET /swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/swagger-resources/**` (dev/test only)
- `GET /dev/mail` (dev only)
- `GET /dev/razorpay-checkout` (dev only, added in Stage 5B)
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
- `PAYOUT_MANAGE`: Create, manage, and mark trainer payout batches as paid.
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
| `RazorpayGatewayStub` | `com.fitflow.payment.gateway.RazorpayGatewayStub` | Deterministic stub for Razorpay order generation and signature verification before live keys in Stage 8. | Verified in payment unit and integration tests. |
| `DefaultFreeTierEntitlementService` | `com.fitflow.booking.DefaultFreeTierEntitlementService` | (Retired in Stage 5) Replaced by `RealEntitlementService` in `membership` with atomic row-locking on `memberships`. | Verified via `EntitlementOrderIT` and `BookingConcurrencyIT`. |

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
15. **Atomic Seat Allocation via Direct SQL Predicate**:
    - *Decision*: Seat allocation is performed in MySQL using `UPDATE class_sessions SET booked_count = booked_count + 1 WHERE id = :id AND booked_count < capacity AND active_flag = 1`.
    - *Rationale*: Guarantees zero overbooking under high concurrent load (verified with 40 concurrent threads over 3 runs against 5 seats) without heavy table or row pessimistic locking.
16. **Deterministic Idempotency Key Engine**:
    - *Decision*: Implemented `IdempotencyService` storing SHA-256 hash of canonical HTTP request payloads in MySQL `idempotency_records` with 24-hour TTL. Duplicate identical requests return cached 201/202 responses; duplicate keys with differing payloads fail immediately with 422 `IDEMPOTENCY_REPLAY_MISMATCH`.
    - *Rationale*: Protects payment, quota, and booking allocations against client network timeouts and retries.
17. **FIFO Waitlist Promotion with Automatic Seat Reservation**:
    - *Decision*: When a confirmed booking is cancelled, the session seat is immediately handed over to the earliest pending waitlist candidate (`status = PENDING` ordered by `created_at ASC`), maintaining `booked_count` unchanged, moving candidate to `PROMOTED`, and creating a new `CONFIRMED` booking.
    - *Rationale*: Eliminates race conditions where an outside member could steal a released seat before waitlisted users could react.
18. **4-Hour Cancellation Cutoff & Entitlement Restore**:
    - *Decision*: Member self-service cancellations within 4 hours of session start time (`startsAt - 4h`) are rejected with 409 `CONFLICT`. Admin cancellations override this restriction. On successful cancellation, the user's booking credit is immediately restored via `EntitlementService`.
    - *Rationale*: Protects trainers and waitlisted members from last-minute seat churn while preserving member fairness.
19. **Trainer Attendance Window & Earnings Snapshots**:
    - *Decision*: Attendance marking and session completion can only occur after `startsAt` has passed. Session completion records an immutable snapshot in `trainer_session_earnings` using `trainer_rate_snapshot_paise` captured at session creation time.
    - *Rationale*: Preserves historical financial auditability against retroactive changes to a trainer's pay rate.
20. **Acyclic Booking-User Boundary via DIP**:
    - *Decision*: Account deletion in `com.fitflow.user` invokes `UserBookingCancellationHandler`, implemented cleanly by `BookingCancellationStub` in `com.fitflow.booking`.
    - *Rationale*: Prevents circular package slice cycles between `user` and `booking`, satisfying ArchUnit rule 7.
21. **Exact Auditable Money Representation via `Money` Record**:
    - *Decision*: Encapsulated monetary amounts into record `Money(long paise)` with zero floating point representations. Banned `double` and `float` primitive and boxed types across `membership`, `payment`, and `payout` packages via ArchUnit Rule 8.
    - *Rationale*: Guarantees total freedom from IEEE-754 binary floating-point roundoff errors in high-value financial calculations.
22. **MinRatio Proration Formula (`MIN_RATIO_V1`)**:
    - *Decision*: Adopted conservative `min(daysRatio, sessionsRatio)` proration policy. Calculations use `BigDecimal` with scale 10 and `HALF_UP` rounding, rounded exactly once at the end to whole paise.
    - *Rationale*: Ensures mathematically reproducible plan upgrades, downgrades, and cancellations while capping cumulative refunds by the net cash remaining in the chain.
23. **Two-Phase Payment Gateway Integration with Pessimistic Locking**:
    - *Decision*: Decoupled payment intents (Phase 1) from settlement (Phase 2). `PaymentFinalizer.finalizePayment` utilizes pessimistic DB row locking (`SELECT ... FOR UPDATE`) with status validation (`status == PENDING`).
    - *Rationale*: Thwarts race conditions and double-spending across concurrent confirm requests, guaranteeing that membership changes and tax-compliant invoice numbers are issued exactly once.
24. **Separation of Duties & Two-Person Approval Rule**:
    - *Decision*: High-value refunds (> limit) and all trainer payout batches enforce two-person governance. The administrator who created or triggered the financial batch cannot be the one to approve it (returns 409 Conflict).
    - *Rationale*: Prevents insider fraud and administrative collusion in financial disbursement pipelines.
25. **Razorpay Gateway Integration, Runtime Credentials & Webhook Security**:
    - *Decision*: Implemented `RazorpayPaymentGateway` using Spring's `RestClient` with HTTP Basic auth (`Key ID : Key Secret`) and timeouts (3s connect, 10s read). No external Razorpay Java SDK is imported. Keys and webhook secrets are stored encrypted at rest via `SecretsService` (`app_secrets`). Switching to `RAZORPAY` requires configured keys and a successful test within the last 10 minutes (`POST /api/v1/admin/settings/payments/razorpay/test`). Public live keys (`rzp_live_`) are guarded by `prod` profile check and `payments.allow-live=true`. Webhooks verify HMAC-SHA256 over raw request body bytes and deduplicate against `payment_webhook_events`. WireMock standalone is used in test scope.
    - *Rationale*: Guarantees zero credentials leaked into files or logs, absolute constant-time cryptographic protection, idempotent webhook handling, and explicit, testable HTTP transport.
26. **Transactional Outbox Secret Splitting & Scrubbing**:
    - *Decision*: Decoupled non-sensitive domain parameters from sensitive credentials (reset tokens, temporary passwords). Sensitive keys are encrypted as one JSON blob via AES-256-GCM into `outbox_events.payload_secret`. The relay includes this ciphertext in `EventEnvelope.enc` and scrubs `payload_secret = NULL` immediately upon broker confirm. Forbidden keys are barred from plain payload JSON.
    - *Rationale*: Eliminates secret exposure in plaintext database tables, broker queues, and application logs (Rule R10).
27. **Relay Concurrency via `FOR UPDATE SKIP LOCKED`**:
    - *Decision*: Poller queries up to 50 rows via `SELECT ... FOR UPDATE SKIP LOCKED` inside one transaction. Rows are published to durable topic exchange `fitflow.events` with publisher confirms enabled. On failure, exponential backoff (`min(2^attempts, 300)s`) is applied; after 10 attempts status transitions to `FAILED`.
    - *Rationale*: Eliminates duplicate publishing and lock contention across multiple monolith cluster instances without distributed locking overhead.
28. **Transport-Agnostic Event Consumer & Idempotent Log Gate**:
    - *Decision*: Handlers implement transport-agnostic `EventHandler.handle(EventEnvelope)`. Asynchronous delivery operates via `RabbitNotificationConsumer`, while local tests and fallback operate via `InMemoryEventDispatcher`. All email notifications check `notification_log.hasAlreadySent(userId, template, eventId)` before sending.
    - *Rationale*: Guarantees zero duplicate email dispatches under network retries, crash recovery, or redelivery.
29. **Dual HTML/Plain-Text Email Rendering & Sliding Window Rate Limiting**:
    - *Decision*: Implemented 19 notification scenarios using Thymeleaf templates (`.html` and `.txt`) with strict escaping and zero health data. Emails are gated by user preference flags (`emailBookings`, `emailReminders`, `emailMarketing`). In-memory `EmailRateLimiter` enforces a 20 emails/hour sliding window limit per recipient.
    - *Rationale*: Ensures compliant, clean communication without message flood vulnerabilities.
30. **Scheduled Jobs Deduplication via Tracking Tables**:
    - *Decision*: `SessionReminderJob` (23–25 hours ahead) and `MembershipExpiringJob` (71–73 hours ahead) run every minute, tracking dispatched notices in MySQL tables `booking_reminders` and `membership_notices`.
    - *Rationale*: Prevents duplicate reminders across repeated job runs or scheduler restarts.
31. **Consent-Governed Health Profiles & AES-256-GCM Storage at Rest**:
    - *Decision*: Health data is decoupled from the base user row and stored encrypted into `member_health_profiles.data_encrypted VARBINARY(4096)` via `HealthDataCodec` using AES-256-GCM with a schema version byte. Data is decrypted in-memory only when handling requests with active DPDP consent. Every access (READ, WRITE, EXPORT, DELETE) writes an immutable record to `health_access_log`. Health DTOs override `toString()` to return `[REDACTED]`. No admin endpoint reads health data (ArchUnit Gate j).
    - *Rationale*: Enforces India's DPDP Act obligations, data minimization, and total confidentiality at rest, in logs, and across application boundaries (Rule R11, Rule I).
32. **Deterministic Safety Rules & Unreviewed Metadata Guard**:
    - *Decision*: Class safety filtering and PAR-Q screening evaluate YAML-configured rules (`safety-rules.yml`, `modifications.yml`) strictly deterministically with zero LLM involvement. Classes with `metadata_reviewed = false` are automatically blocked (`METADATA_UNREVIEWED`). Members with `RED_FLAG` status without medical clearance are restricted to gentle LOW intensity classes with zero contraindication movement tags.
    - *Rationale*: Guarantees mathematically predictable participant safety without non-deterministic hallucinations or calculation drifts (Rule R9).
33. **Mifflin-St Jeor Energy Math & Calorie Floor Protection**:
    - *Decision*: Class burn (`MET * weight * (mins/60)`), BMR (Mifflin-St Jeor with UNSPECIFIED midpoint -78), TDEE, and macro targets use `BigDecimal` with rounding applied once at the end (`RoundingMode.HALF_UP`). Goal target calories never drop below 1200 kcal (FEMALE/UNSPECIFIED) or 1500 kcal (MALE). Underweight members (BMI < 18.5) receive maintenance calories with an `UNDERWEIGHT_NO_DEFICIT` note.
    - *Rationale*: Eliminates floating-point drift and guarantees nutritional safety.
34. **Anonymous Trainer Class Prep & Minimum 3 Attendee Privacy Gate**:
    - *Decision*: Class prep summary (`GET /api/v1/trainer/sessions/{id}/prep`) provides aggregated injury area counts and deduplicated movement modifications only if at least 3 confirmed attendees have active `SHARE_TRAINER_SUMMARY` consent. If fewer than 3 attend, summary counts and modifications are withheld (`summaryAvailable = false`).
    - *Rationale*: Prevents individual member deanonymization in small class sessions.
35. **Multi-Agent Framework Decision Gate (LangChain4j 1.22.0)**:
    - *Decision*: Evaluated LangChain4j (`dev.langchain4j:langchain4j:1.22.0`) and Google ADK for Java against 5 strict criteria (Maven Central release, Gemini function calling, testable fake chat model, isolated prompts/tools/memory, custom PostgreSQL memory). LangChain4j satisfied all 5 criteria with generally available stable artifacts on Maven Central. All LLM calls are decoupled behind thin port interfaces (`ChatModelPort`, `AgentRuntime`, `ScriptedChatModel`) enabling seamless replacement.
    - *Rationale*: Guarantees production stability, offline testability, zero flaky external calls during CI, and strict separation from proprietary framework lock-in.
36. **Deterministic Supervisor & Role-Partitioned Specialist Agents**:
    - *Decision*: Supervisor routing is implemented entirely in deterministic Java (`SupervisorRouter`). The caller's authenticated roles dictate candidate agents (`MEMBER` -> `BookingAgent`, `MembershipAgent`, `IntakeAgent`, `SafeClassMatcherAgent`). Intent classification utilizes rule-based keyword/regex pattern matching (`intents.yml`), with single-token LLM classification as fallback for ambiguous inputs. Prompt injection cannot bypass role-based agent dispatch (G4 RoleGuard).
    - *Rationale*: Deterministic code—not non-deterministic LLMs—governs permissions and boundaries.
37. **Human-in-the-Loop Proposal & Confirmation Architecture**:
    - *Decision*: Specialist agents are prohibited from directly executing mutative actions (booking creation/cancellation, membership subscribe/upgrade/cancel, profile updates). Instead, agents propose actions into PostgreSQL `pending_actions` (10-minute expiry) and instruct the member to review on-screen and confirm. Execution requires explicit caller confirmation (`POST /api/v1/agent/actions/{id}/confirm`) with strict IDOR ownership checks, real-time role/permission re-verification, and idempotency key `agent-action-{id}`. Subsequent confirmations replay stored results without re-executing.
    - *Rationale*: Prevents automated financial or state mutation without explicit user consent and auditability (Rule R8).
38. **7-Layer Guardrails & Health Canary Isolation**:
    - *Decision*: Implemented 7 security and safety guardrails: G1 `NumberGuard` (blocks hallucinated prices/quotas; retries once then supplies deterministic tool summary), G2 `InjectionGuard` (strips control characters, truncates to 300 chars, wraps in `[UNTRUSTED DATA]...[/UNTRUSTED DATA]`), G3 `ScopeGuard` (blocks medical diagnoses, legal advice, other members' data), G4 `RoleGuard` (verifies permissions before tool execution), G5 `HealthEgressGuard` (prevents health data egress; verifies zero health canary `CANARY-9f3a` leakage), G6 `OutputHygieneGuard` (enforces medical disclaimer presence), G7 `NoSecretsGuard` (blocks API keys, tokens, and cryptographic secrets).
    - *Rationale*: Guarantees conversational safety, strict compliance with India's DPDP Act, and complete confidentiality.
39. **Agent Tool Registry & Zero Caller ID Rule (ArchUnit Rules K & L)**:
    - *Decision*: 12 specialist tools registered in `ToolRegistry`. All tool argument DTOs accept zero caller identity parameters (`userId`, `memberId`, etc.), which are populated exclusively from authenticated `ToolContext` (ArchUnit Rule K). Tools never inject or invoke JPA repositories/entities directly; all operations traverse domain services (`BookingService`, `MembershipService`, `WellnessProfileService`) to preserve transaction boundaries and business logic invariants (ArchUnit Rule L).
    - *Rationale*: Eliminates IDOR attack vectors at the tool interface and preserves domain encapsulation.
40. **Multi-Agent Specialist Expansion & 11 Coded Intent Domains**:
    - *Decision*: Expanded multi-agent ecosystem to 8 new specialist agents across MEMBER (`PlanBuilderAgent`, `WellnessCoachAgent`, `NutritionNumbersAgent`), TRAINER (`TrainerPrepAgent`, `TrainerScheduleAgent`), and ADMIN (`RetentionAgent`, `ClassMetadataAssistant`, `DemandForecastAgent`). `SupervisorRouter` partitions intent into 11 domains with zero model-driven privilege escalation.
    - *Rationale*: Enforces role-based agent isolation and least privilege across platform user classes.
41. **Deterministic Weekly Plan Generation & Greedy Recovery Rules**:
    - *Decision*: `WeeklyPlanService` selects candidate sessions deterministically obeying remaining session entitlements, goal category preferences (`goal-categories.yml`), recovery rules (at most 2 consecutive HIGH intensity days; beginners capped at 2 HIGH per week, advanced at 3), and category variety limits (max 2 sessions per category). The model can only propose a strict subset of generated candidates. Confirmations book sessions individually with idempotency keys `agent-<actionId>-<sessionId>`.
    - *Rationale*: Guarantees physical recovery and entitlement preservation mathematically without LLM hallucination.
42. **Chunked SSE Streaming & Single-Stream Concurrency Enforcement**:
    - *Decision*: SSE streaming endpoints (`GET/POST /api/v1/agent/chat/stream`) emit typed SSE events (`token`, `tool`, `pending_action`, `guard`, `done`, `error`). Crucially, raw model tokens are never streamed directly to the client; instead, `NumberGuard` inspects the final full response text before the response is streamed in chunked tokens. A concurrent map limit restricts each user to exactly 1 active stream at a time.
    - *Rationale*: Prevents unverified or hallucinated values from flashing on user screens before guard inspection.
43. **Scheduled Agent Jobs with Distributed Row Locking**:
    - *Decision*: Implemented `weeklyCoachSummaryJob` (Monday morning member weekly progress summaries with NumberGuard-verified coach text), `atRiskDetectionJob` (churn detection for members with >=50% attendance drop or expiring paid plans, emitting `retention.nudge`), and `pendingActionExpiryJob` (expiring unconfirmed actions). All jobs acquire an advisory row lock in `job_locks` and log execution metrics to `job_runs`.
    - *Rationale*: Ensures cluster-safe, single-instance execution of automated background agent jobs.
44. **Comprehensive 60-Scenario Evaluation Suite**:
    - *Decision*: Created `agent-evals/*.json` containing 60 structured test scenarios: 20 normal, 10 safety, 20 red-team (adversarial prompts, injection attacks, cross-tenant probing), and 10 robustness (timeouts, malformed arguments, circuit breaker). Executed via `EvalRunnerTest` with zero network dependency using `ScriptedChatModel`.
    - *Rationale*: Provides rigorous regression testing and mathematical assurance that safety guardrails hold under adversarial conditions.
45. **Frontend Technology Decisions & Version Pinning**:
    - *Decision*: Standardized on React 18.3.1 + TypeScript (strict) 5.6.3 + Vite 5.4.10 single-page application. Pinned exact stable versions: React Router DOM 6.27.0, Redux Toolkit 2.3.0 + RTK Query with react-redux 9.1.2, Tailwind CSS 3.4.14 with design tokens as CSS variables, Radix UI headless accessible primitives (@radix-ui/react-dialog 1.1.2, @radix-ui/react-dropdown-menu 2.1.2, @radix-ui/react-tabs 1.1.1, @radix-ui/react-toast 1.2.2, @radix-ui/react-select 2.1.2, @radix-ui/react-switch 1.1.1, @radix-ui/react-checkbox 1.1.2, @radix-ui/react-radio-group 1.2.1, @radix-ui/react-tooltip 1.1.3), lucide-react 0.453.0 icons, React Hook Form 7.53.1 + Zod 3.23.8 + @hookform/resolvers 3.9.0 for validated forms, Vitest 2.1.3 + React Testing Library 16.0.1 + MSW 2.6.0 for unit/integration tests, Playwright 1.48.2 + @axe-core/playwright 4.10.0 for end-to-end tests and accessibility gates, openapi-typescript 7.4.1 for type generation. No other UI kit or CSS-in-JS runtime permitted.
    - *Rationale*: Guarantees predictable dependency trees, headless accessibility compliance (WCAG 2.2 AA), type safety from OpenAPI backend specifications, and fast zero-fluff client builds.
46. **Browser Authentication Architecture**:
    - *Decision*: In-browser security stores access tokens strictly in memory (via Redux state closure). Refresh tokens reside exclusively in httpOnly, SameSite=Strict cookies. LocalStorage and SessionStorage are strictly forbidden for credentials or JWT tokens.
    - *Rationale*: Mitigates XSS token exfiltration attacks while allowing seamless silent token refresh.
47. **Anti-Drift Screen-to-Endpoint Mapping & Verification Gate**:
    - *Decision*: Created `docs/design/screen-endpoint-map.yml` detailing all 46 application screens, their roles, routes, and referenced OpenAPI endpoints. Enforced via `scripts/check-screen-map.js` which verifies zero duplicated routes/IDs, role legitimacy, and exact endpoint existence in `openapi.json`.
    - *Rationale*: Prevents UI/UX drift from backend API contracts across development iterations.
48. **Design System Tokens & Automated Contrast Verification**:
    - *Decision*: Formulated design tokens in `docs/design/tokens.json` and `tokens.css`. Created automated gate `scripts/check-contrast.js` calculating relative luminance and verifying WCAG 2.2 AA contrast (4.5:1 body, 3.0:1 UI) across 26 semantic color pairs in both Light and Dark themes. Created standalone static style guide `docs/design/styleguide/index.html` passing axe-core with zero critical/serious violations.
    - *Rationale*: Enforces universal accessibility standards mathematically across all viewports and color modes.

---

## 11. Design File Index & Error-to-UX Mapping

### Design File Index
- [`docs/api/openapi.json`](file:///c:/Users/shrey/Downloads/FitFlow/docs/api/openapi.json): Current OpenAPI 3.1.0 snapshot with sorted keys (108 paths, 119 endpoints).
- [`docs/design/screen-endpoint-map.yml`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/screen-endpoint-map.yml): Machine-readable contract mapping 46 screens to backend endpoints.
- [`docs/design/tokens.json`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/tokens.json): Machine-readable design tokens (palette, semantic pairs, typography, spacing, radii, motion, breakpoints).
- [`docs/design/tokens.css`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/tokens.css): Compiled CSS custom properties for Light and Dark themes.
- [`docs/design/design-system.md`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/design-system.md): Comprehensive design system and UX specification.
- [`docs/design/components.md`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/components.md): Catalogue of 38 reusable accessible UI components.
- [`docs/design/screens.md`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/screens.md): Screen inventory with ASCII wireframes for all 46 screens (dual desktop/mobile for 6 screens).
- [`docs/design/flows.md`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/flows.md): 7 end-to-end Mermaid user flows with success and failure paths.
- [`docs/design/styleguide/index.html`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/styleguide/index.html): Self-contained interactive style guide with live theme toggle.
- [`docs/design/copy-deck.md`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/copy-deck.md): Approved master copy deck.
- [`frontend/src/i18n/en.json`](file:///c:/Users/shrey/Downloads/FitFlow/frontend/src/i18n/en.json): Frontend message translation catalogue.
- [`docs/design/requirements-traceability.md`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/requirements-traceability.md): Requirements traceability matrix.

### RFC 7807 Error Code to UX Mapping

| Error Code | User-Facing Display Message | Primary Recovery Action |
| :--- | :--- | :--- |
| `UPGRADE_REQUIRED` | *"You have used all session credits included in your plan."* | Displays plan comparison card with `"Upgrade Plan"` button. |
| `SESSION_FULL` | *"This class is currently full."* | Prompts `"Join Waitlist"` button with position indicator. |
| `CONSENT_REQUIRED` | *"Health data consent is needed before accessing personalized wellness features."* | Redirects with return URL to `/app/wellness/consent`. |
| `AGE_RESTRICTED` | *"You must be 18 or older to access nutrition calculations and workout safety screening."* | Explains DPDP Act age gate; disables wellness intake. |
| `RATE_LIMITED` | *"Too many requests. Please wait a moment before trying again."* | Disables trigger button with countdown timer from `Retry-After`. |
| `OPTIMISTIC_LOCK` | *"This class was just modified by another user. Reloading fresh availability."* | Prompts `"Refresh Details"` to fetch latest seats. |
| `PAYMENT_FAILED` | *"Your payment was not completed by the issuing bank."* | Keeps checkout open; prompts `"Try Another Payment Method"`. |
| `UNAUTHENTICATED` | *"Your session has expired. Please sign in to continue."* | Preserves current URL and redirects to `/login`. |
| `FORBIDDEN` | *"You do not have permission to view this page or perform this action."* | Navigates to accessible `/403` explanation page. |
| `BOOKING_RULE_VIOLATION` | *"Booking cutoff window has passed (classes lock 2 hours before start)."* | Highlights class status as locked; suggests upcoming sessions. |
| `LOCKED` | *"Account locked due to 5 consecutive login failures."* | Displays locked banner with remaining lockout minutes. |
| `VALIDATION_FAILED` | *"Please correct the highlighted errors."* | Summarizes field errors at top; focuses first invalid input. |
| `INTERNAL_ERROR` | *"We're experiencing temporary technical difficulties. Please try again shortly."* | Renders friendly ErrorState banner with retry button. |

---

## 12. Assumptions

1. **Currency**: All money amounts are stored as `BIGINT` representing paise (INR). No floating point arithmetic is permitted for monetary calculations (Rule R4).
2. **Time**: All dates and timestamps are stored in UTC using `java.time.Instant`. Time is always accessed via the injected `java.time.Clock` bean (Rule R5).
3. **Display Timezone**: Documented as `Asia/Kolkata` (`fitflow.display-timezone`), used solely for user-facing UI formatting; internal persistence is strictly UTC.
4. **Dual Database Consistency**: MySQL and PostgreSQL are eventually consistent via the transactional Outbox Pattern (`outbox_events`). Distributed 2PC (XA) transactions are explicitly avoided.
5. **Gemini API Key**: The Gemini LLM key is entered at runtime by an administrator into `app_secrets` (encrypted at rest), with an optional fallback to `GEMINI_API_KEY` in environment variables.
6. **Proration Policy Assumption**: The `MIN_RATIO_V1` formula (`daysRemaining / totalDays`, `sessionsRemaining / sessionsTotal`, `refundableFraction = min(daysRatio, sessionsRatio)`, cancellation fee deduction, and net cash cap) is a business assumption chosen by the platform case-study author.
7. **Wellness Suggestions Disclaimer Obligation**: "Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use." embedded across safety rule headers (`safety-rules.yml`, `modifications.yml`), API response bodies (`disclaimer`), and platform documentation.

---

## 13. Stage Log

| Stage | Date | Built | Verified How | UNVERIFIED Items | Known Gaps |
| :---: | :---: | :--- | :--- | :--- | :--- |
| **Stage 1** | 2026-10-27 | Repository scaffold, Maven build with Java 17, Dual MySQL/PostgreSQL DataSources & Flyway migrations, AES-256-GCM EncryptionService, SecretsService, ErrorCode & GlobalExceptionHandler, CorrelationIdFilter, PageRequestDto, ArchUnit 7 architecture rules, GreenMail dev mail controller, DevSeedRunner. | `mvn verify` green (35 tests: 25 unit + 10 IT); Jacoco coverage check passed; Live health check UP; 3 Sanity-Breaks executed and proven. | None | Authentication workflows (JWT issuance, login endpoints) scheduled for Stage 2. |
| **Stage 2** | 2026-10-28 | Production-grade JWT auth (`TokenService`, JJWT), BCrypt(12), role & permission model (`PermissionChecker`), token rotation with family revocation on reuse, `PasswordPolicy`, forgot/reset password with outbox events, rate limiting & lockout (`LoginAttemptService`, Bucket4j), `AdminActionLogger`, `AgeService`, `EndpointSecurityCoverageTest` automated gate. | `mvn verify` green (90 tests: 51 unit + 39 IT); Jacoco coverage check passed; EndpointSecurityCoverageTest passed; Live app walkthrough (10 endpoints) passed; 3 Sanity-Breaks executed and proven. | None | User, trainer, and admin management scheduled for Stage 3. |
| **Stage 3** | 2026-10-28 | Member self-service (`GET/PATCH /api/v1/me`, `GET /export`, `POST /delete-request`), Admin user management with pagination, filtering & last-super-admin guard (`/api/v1/admin/users`), Trainer catalog & self-service availability validation (`/api/v1/trainers`, `/api/v1/trainer/me`, `/rate`), AI runtime settings with AES-256-GCM encrypted Gemini key & mock fallback (`/api/v1/admin/settings/ai`), `BookingCancellationStub`, `GeminiClientStub`. | `mvn verify` green (160 tests: 66 unit + 94 IT); Jacoco coverage check passed; EndpointSecurityCoverageTest passed; ArchUnit 7 gates passed; Live app walkthrough (21 endpoints) passed; 3 Sanity-Breaks executed and proven. | None | Fitness class catalog, room scheduling, and booking engine scheduled for Stage 4. |
| **Stage 4** | 2026-10-28 | Fitness class catalog & metadata vocabularies (`/classes`, `/admin/classes`), session scheduling with recurrence & room/trainer conflict detection (`/sessions`, `/admin/sessions`), concurrent booking pipeline with atomic seat allocation (`/bookings`), FIFO waitlist promotion, 4h cancellation cutoff, idempotency key engine (`idempotency_records`), trainer attendance & earnings snapshot (`/trainer/sessions/{id}/attendance`, `/complete`), session utilization reporting. | `mvn verify` green (225 tests: 79 unit + 146 IT); Jacoco coverage check passed; EndpointSecurityCoverageTest passed (22 new endpoints guarded); ArchUnit 7 gates passed; 3 Sanity-Breaks executed and proven; Concurrency test (40 threads, 3 runs) 0 overbooking proven; Live app walkthrough (12 steps + direct MySQL CLI verification) passed. | None | Membership plans, subscriptions, quota tracking, and payment processing scheduled for Stage 5. |
| **Stage 5** | 2026-10-28 | Exact auditable money arithmetic (`Money` record, ArchUnit double/float ban in financial packages), `MIN_RATIO_V1` proration policy, 30-case boundary table test, 1000-iteration randomized property test, two-phase payment gateway router (`MockPaymentGateway`, `RazorpayGatewayStub`), pessimistic payment finalizer, membership subscribe/upgrade/cancel, atomic row-locking entitlement service (`RealEntitlementService`), auto-assigned FREE tier, admin payout batches & two-person approval rule. | `mvn verify` green (250 tests: 85 unit + 165 IT); Jacoco coverage check passed; EndpointSecurityCoverageTest passed; ArchUnit 8 rules passed; 4 Sanity-Breaks executed and proven; 20-thread concurrent confirm 0 duplicate execution proven; Live app walkthrough (10 steps + CSV export) passed. | None | Transactional notifications & outbox consumer scheduled for Stage 6. |
| **Stage 5B** | 2026-10-28 | Razorpay integration behind PaymentGateway (`RazorpayPaymentGateway`, `RazorpayClient`), runtime encrypted credentials (`GET/PUT/DELETE /api/v1/admin/settings/payments/razorpay`), live key guards (`payments.allow-live`), 10-minute test window requirement (`PUT /gateway`, `POST /test`), constant-time HMAC-SHA256 signature and webhook verification (`POST /api/v1/payments/webhooks/razorpay`), webhook deduplication (`payment_webhook_events`), Razorpay refund API, Reconcile job extension for pending payments and refunds, dev checkout UI (`/dev/razorpay-checkout`), client payload endpoint (`/payments/{id}/client-payload`). WireMock standalone stubbing. ArchUnit Razorpay boundary isolation rule (Rule 9). | `mvn verify` green (71 payment suite tests: 10 unit + 61 IT); ArchUnit Rule 9 passed; EndpointSecurityCoverageTest passed; 3 Sanity-Breaks executed and proven; WireMock mock suite passed. | None | Transactional notifications & outbox consumer scheduled for Stage 6. |
| **Stage 6** | 2026-10-28 | Reliable transactional outbox relay with `SKIP LOCKED`, publisher confirms, exponential backoff, RabbitMQ topic topology (`fitflow.events`, DLX, queues `q.notifications.email`, `q.agent.events`), outbox secret splitting & scrubbing (`payload_secret = NULL`), transport-agnostic consumer (`EventHandler`), idempotent email delivery via PostgreSQL `notification_log`, 19 Thymeleaf dual HTML/text notification templates, 20/hr recipient sliding window rate limiter, scheduled reminder & expiry notice jobs (`SessionReminderJob`, `MembershipExpiringJob`), Admin Messaging controls (`/api/v1/admin/messaging/outbox`, `/retry`, `/queues`, `/dlq/{queue}/replay`), Outbox health indicator. | `mvn verify` green (125 tests: 73 unit + 52 IT); Jacoco coverage check passed (>= 70%); ArchUnit 9 rules passed; EndpointSecurityCoverageTest passed; 3 Sanity-Breaks executed and proven; 5-scenario Live Walkthrough (`Stage6WalkthroughIT`) passed. | None | Member health profiles, consent (DPDP Act), deterministic screening, and energy math scheduled for Stage 7. |
| **Stage 7** | 2026-10-28 | Member health profiles, consent (DPDP Act, `v1.md`), age 18+ enforcement (`AgeService`), encrypted storage at rest (`member_health_profiles.data_encrypted` via `HealthDataCodec`), immutable `health_access_log`, DPDP export & hard deletion, deterministic screening (PAR-Q Q1..Q7, conditions, injuries), deterministic safety filtering (`safety-rules.yml`, `modifications.yml`), unreviewed metadata guard, non-floating-point energy & nutrition math (`CalorieService`, Mifflin-St Jeor, floor protection, underweight guard), anonymous trainer class prep (min-3 attendee gate), weekly workout progress & streak calculation, attended session feedback, restriction proposals, outbox events (`consent.granted`, `consent.revoked`, `profile.completed`, `profile.updated`, `health.data_deleted`, `feedback.pain_reported`). ArchUnit health package isolation (Gate j). | `mvn verify` green (125 tests: 104 unit + 21 IT); Jacoco coverage check passed (>= 70%); ArchUnit 10 rules passed; EndpointSecurityCoverageTest passed; 4 Sanity-Breaks executed and proven; End-to-End Live Walkthrough (`Stage7WalkthroughIT`) passed. | None | Autonomous agent architecture, memory, tools, and supervisor scheduled for Stage 8. |
| **Stage 8** | 2026-10-29 | Multi-agent framework decision gate (LangChain4j 1.22.0), deterministic `SupervisorRouter` with YAML intent pattern catalog (`intents.yml`), 4 specialist agents (`BookingAgent`, `MembershipAgent`, `IntakeAgent`, `SafeClassMatcherAgent`), 12 specialist tools with zero caller ID args and zero repo dependencies, 7 guardrails (G1 NumberGuard, G2 InjectionGuard, G3 ScopeGuard, G4 RoleGuard, G5 HealthEgressGuard, G6 OutputHygieneGuard, G7 NoSecretsGuard), PostgreSQL V3 schema (`agent_usage`, `pending_actions.result_json`), human-in-the-loop pending actions (`PendingActionService`), `AgentCircuitBreaker`, sliding window and daily usage limiter (`AgentUsageService`), SHA-256 redacted audit logging (`AgentAuditService`), ArchUnit Rules K & L. | `mvn verify` green (103 agent suite tests + full suite passing); EndpointSecurityCoverageTest passed; ArchUnit 12 rules passed; 4 Sanity-Breaks executed and proven; Multi-turn Walkthrough (`Stage8WalkthroughIT`) passed. | None | Trainer and admin specialist agents, scheduled background agents, and proactive outreach scheduled for Stage 9. |
| **Stage 9** | 2026-10-29 | Multi-Agent expansion with 8 new specialist agents across Member, Trainer, and Admin; 15 new tools; 11-intent SupervisorRouter; deterministic WeeklyPlanService with recovery/variety rules; AtRiskService and DemandService; pending action proposals and execution; single-stream chunked SSE streaming (`GET/POST /api/v1/agent/chat/stream`) with post-guard verification; scheduled agent jobs (`weeklyCoachSummaryJob`, `atRiskDetectionJob`, `pendingActionExpiryJob`) with `job_locks` and `job_runs`; notification templates (`weekly-summary`, `retention-nudge`); 60-scenario evaluation harness (`agent-evals/*.json`) running 100% offline via ScriptedChatModel (60/60 PASS); ArchUnit Rule M (Admin tool health data ban). | `mvn verify` green (150+ agent suite tests passing); 60/60 Eval scenarios passing (`docs/evidence/stage-09-evals.txt`); ArchUnit 13 rules passing; 4 Sanity-Breaks executed and proven; Live dev walkthrough verified. | None | Production deployment, containerization considerations (outside Antigravity rules), and mobile client integration scheduled for future stages. |
| **Stage 10** | 2026-10-29 | Complete UI/UX design specification for unified Member, Trainer, and Admin web application. Frontend stack decisions pinned (React 18.3.1, Vite, TypeScript, RTK Query, Tailwind CSS, Radix UI, lucide-react, Playwright, axe-core). OpenAPI 3.1.0 snapshot exported (`docs/api/openapi.json`). Anti-drift contract `docs/design/screen-endpoint-map.yml` linking all 46 screens to 119 backend endpoints; verified via `scripts/check-screen-map.js`. WCAG 2.2 AA design tokens (`tokens.json`, `tokens.css`) verified via `scripts/check-contrast.js` (26/26 PASS). 38 accessible components (`components.md`), 46 screen inventories with ASCII wireframes (`screens.md`, dual desktop/mobile for 6 key screens), 7 Mermaid user flows with failure paths (`flows.md`), standalone interactive style guide (`styleguide/index.html`) verified via Playwright/axe-core with 0 serious/critical violations, 4 viewport screenshots, master copy deck (`copy-deck.md`), message catalogue draft (`frontend/src/i18n/en.json`), and requirements traceability matrix (19/19 requirements mapped with 0 gaps). | `scripts/check-screen-map.js` passed (normal + intentional error failure + restored); `scripts/check-contrast.js` passed (26/26 color pairs); Playwright axe-core audit passed (0 critical, 0 serious); 4 style guide screenshots captured; `scripts/verify.ps1` green (234/234 tests pass + live health UP). | None | React application implementation, component building, and auth cookie wiring scheduled for Stage 11. |
