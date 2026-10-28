# FitFlow Stage 6 Evidence Report: Reliable Events, RabbitMQ Outbox Relay & Email Notifications

## 1. Architectural Summary & System Contract

Stage 6 implements guaranteed at-least-once asynchronous event delivery and email notification pipelines for the FitFlow platform without compromising data confidentiality:
- **Transactional Outbox with Secret Splitting**: Business domains append events to MySQL `outbox_events` inside their own transaction (`Propagation.MANDATORY`). Non-sensitive parameters reside in `payload` JSON, while sensitive credentials (temporary passwords, reset tokens) are encrypted via AES-256-GCM (`EncryptionService`) into `payload_secret`.
- **Secret Scrubbing**: Once an outbox record is successfully published and confirmed by the broker (`SENT`), `payload_secret` is immediately scrubbed to `NULL`.
- **Scheduled Polling Relay with `SKIP LOCKED`**: An active poller runs every second (`fitflow.outbox.poll-interval-ms=1000`), executing `SELECT * FROM outbox_events WHERE status = 'PENDING' AND next_attempt_at <= NOW() ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED` inside a transaction. This allows concurrent relays to run across nodes without duplicating work or blocking each other.
- **RabbitMQ Topic Topology**: Durable topic exchange `fitflow.events` and dead-letter exchange `fitflow.events.dlx`. Durable queues `q.notifications.email` and `q.agent.events`, with DLQs `q.notifications.email.dlq` and `q.agent.events.dlq`.
- **Publisher Confirms & Backoff**: The relay marks an outbox row as `SENT` only after receiving an explicit publisher confirm (ACK) from RabbitMQ. On failure, attempts are incremented with exponential backoff (`min(2^attempts, 300)` seconds); after 10 failed attempts, status is marked `FAILED`.
- **Transport-Agnostic & Idempotent Consumer**: Handlers implement `EventHandler`. Duplicate events are deduplicated against PostgreSQL `notification_log`.
- **Dual HTML & Plain-Text Email System**: 19 domain notification scenarios rendered using Thymeleaf templates (`resources/mail/*.html` and `resources/mail/*.txt`) with strict HTML escaping, user preference gating (`BOOKINGS`, `REMINDERS`, `MARKETING`), sliding window rate limiting (max 20 emails/hour per user), and zero health data.
- **Scheduled Notice Jobs**: `SessionReminderJob` (23–25 hours ahead) and `MembershipExpiringJob` (71–73 hours ahead) tracking dispatches in `booking_reminders` and `membership_notices`.
- **Admin Messaging & DLQ Controls**: `/api/v1/admin/messaging/outbox`, `/outbox/{id}/retry`, `/queues`, and `/dlq/{queue}/replay` with strict permission gates, 100-message replay clamping, and admin action audit logging.

---

## 2. Sanity Breaks (Proof of Robustness)

Three intentional mutations were introduced into the code to prove that core safety guarantees fail when expected, and recover completely when restored.

### Break 1: Concurrency Safety (`SKIP LOCKED`)
- **Component**: `backend/src/test/java/com/fitflow/notification/OutboxRelayTest.java` / `OutboxRelayService.java`
- **Break Mechanism**: Removed `SKIP LOCKED` simulation; all concurrent threads read the same pending outbox rows from the head of the queue.
- **Failure Output**:
  ```text
  org.opentest4j.AssertionFailedError: 
  Expected :500
  Actual   :1877
      at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:55)
      at org.junit.jupiter.api.AssertionUtils.failNotEqual(AssertionUtils.java:62)
      at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:182)
      at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1152)
      at com.fitflow.notification.OutboxRelayTest.testConcurrentRelaysPublishEachRowExactlyOnce(OutboxRelayTest.java:254)
  ```
- **Resolution**: Restored `SELECT ... FOR UPDATE SKIP LOCKED`.
- **Restored Pass Output**:
  ```text
  [INFO] Running com.fitflow.notification.OutboxRelayTest
  [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s -- in com.fitflow.notification.OutboxRelayTest
  ```
  All 500 outbox rows were published exactly once across 4 concurrent relay worker threads.

---

### Break 2: Idempotent Consumer Gate (`notification_log` Check)
- **Component**: `backend/src/main/java/com/fitflow/notification/EmailNotificationConsumer.java`
- **Break Mechanism**: Commented out the `notificationLogDao.hasAlreadySent(...)` check so incoming duplicate messages immediately proceed to mail delivery.
- **Failure Output**:
  ```text
  org.mockito.exceptions.verification.TooManyActualInvocations: 
  emailService.sendEmail(
      eq(44L),
      <any string>,
      eq("booking-confirmed"),
      <any string>,
      <any>
  );
  Wanted 1 time:
  -> at com.fitflow.notification.ConsumersTest.testSameEventTwiceSendsOneEmail(ConsumersTest.java:91)
  But was 2 times:
  -> at com.fitflow.notification.EmailNotificationConsumer.sendNotificationEmail(EmailNotificationConsumer.java:132)
  -> at com.fitflow.notification.EmailNotificationConsumer.sendNotificationEmail(EmailNotificationConsumer.java:132)
  ```
- **Resolution**: Restored `hasAlreadySent` check against `notification_log`.
- **Restored Pass Output**:
  ```text
  [INFO] Running com.fitflow.notification.ConsumersTest
  [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.345 s -- in com.fitflow.notification.ConsumersTest
  ```
  The duplicate event was recognized as previously processed and skipped without triggering an outbound email.

---

### Break 3: Outbox Secret Scrubbing (`payload_secret = NULL`)
- **Component**: `backend/src/main/java/com/fitflow/notification/OutboxRelayService.java`
- **Break Mechanism**: Commented out `event.setPayloadSecret(null)` when updating the outbox row to `Status.SENT`.
- **Failure Output**:
  ```text
  org.opentest4j.AssertionFailedError: payload_secret must be scrubbed (null) after SENT ==> 
  Expected :null
  Actual   :encrypted-secret-token
      at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
      at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)
      at org.junit.jupiter.api.AssertNull.assertNull(AssertNull.java:49)
      at org.junit.jupiter.api.Assertions.assertNull(Assertions.java:279)
      at com.fitflow.notification.OutboxRelayTest.testPublishesPendingInIdOrderAndScrubsSecret(OutboxRelayTest.java:107)
  ```
- **Resolution**: Restored `event.setPayloadSecret(null)` prior to database update.
- **Restored Pass Output**:
  ```text
  [INFO] Running com.fitflow.notification.OutboxRelayTest
  [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s -- in com.fitflow.notification.OutboxRelayTest
  ```
  Confirmed that sensitive ciphertext blobs are deleted from `outbox_events` as soon as the message is handed off to the message broker.

---

## 3. Live Walkthrough Execution Logs (`Stage6WalkthroughIT`)

Full integration test executed against live local MySQL (port 3306), PostgreSQL (port 5432), RabbitMQ (port 5672), and GreenMail SMTP (port 3025).

```text
================================================================================
STAGE 6 LIVE WALKTHROUGH EXECUTION
================================================================================

--- SCENARIO 1: Booking confirmation via RabbitMQ & Email ---
[Step 1a] Queue 'q.notifications.email' initial message depth: 0
2026-10-28 21:02:02.850 [main] [NO_TRACE] [ANON] INFO  c.f.messaging.RabbitMqEventPublisher - Successfully published event id=1793201522817 type='booking.created' with broker ACK
[Step 1b] Published 'booking.created' event (eventId=1793201522817) to topic exchange 'fitflow.events'
2026-10-28 21:02:02.878 [org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1] [NO_TRACE] [ANON] INFO  c.f.n.EmailNotificationConsumer - Processing notification event id=1793201522817 type='booking.created'
2026-10-28 21:02:08.238 [smtp:127.0.0.1:3025<-/127.0.0.1:60923] [NO_TRACE] [ANON] INFO  c.i.greenmail.user.UserManager - Created user login dev@fitflow.local for address dev@fitflow.local with password dev@fitflow.local because it didn't exist before.
2026-10-28 21:02:08.254 [org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1] [NO_TRACE] [ANON] INFO  c.fitflow.notification.EmailService - Sent email template='booking-confirmed' to userId=108642 successfully
[Step 1c] GreenMail received email:
  To: dev@fitflow.local
  Subject: Booking Confirmed
2026-10-28 21:02:08.256 [org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1] [NO_TRACE] [ANON] INFO  c.f.m.RabbitNotificationConsumer - Consumer successfully processed and ACKed messageId=1793201522817
[Step 1d] DevMailController /dev/mail verified. Count: 1

--- SCENARIO 2: Broker Outage, Exponential Backoff & Recovery ---
[Step 2a] Created outbox event id=1555 status=PENDING
2026-10-28 21:02:08.371 [main] [NO_TRACE] [ANON] DEBUG c.f.notification.OutboxRelayService - Found 1 pending outbox events for relay
2026-10-28 21:02:08.372 [main] [NO_TRACE] [ANON] WARN  c.f.notification.OutboxRelayService - Relay failed for outbox event id=1555 attempt 1/10, next attempt at 2026-10-28T15:32:10.372071Z: Simulated Broker Connection Refused (TCP:5672)
[Step 2b] After broker outage attempt:
  Status: PENDING
  Attempts: 1
  Last Error: Simulated Broker Connection Refused (TCP:5672)
  Next Attempt At: 2026-10-28T15:32:10.372071Z
[Step 2c] Admin endpoint /api/v1/admin/messaging/outbox?status=PENDING shows 1 pending events
2026-10-28 21:02:08.421 [main] [NO_TRACE] [ANON] DEBUG c.f.notification.OutboxRelayService - Found 1 pending outbox events for relay
2026-10-28 21:02:08.425 [main] [NO_TRACE] [ANON] INFO  c.f.messaging.RabbitMqEventPublisher - Successfully published event id=1555 type='booking.created' with broker ACK
2026-10-28 21:02:08.425 [main] [NO_TRACE] [ANON] INFO  c.f.notification.OutboxRelayService - Relayed outbox event id=1555 type='booking.created' successfully
[Step 2d] After broker recovered & relay executed:
  Status: SENT
  Sent At: 2026-10-28T15:32:08.425874Z
[Step 2e] GreenMail received recovered email: Booking Confirmed

--- SCENARIO 3: Forgot Password Confidentiality & Direct SQL Check ---
2026-10-28 21:02:13.133 [main] [601bdde7-aa02-496d-86bd-8186fa7f8e4e] [ANON] INFO  c.fitflow.notification.OutboxService - Appended outbox event id=1556 type='password.reset_requested' aggregateType='USER' aggregateId=108642
2026-10-28 21:02:13.133 [main] [601bdde7-aa02-496d-86bd-8186fa7f8e4e] [ANON] INFO  c.fitflow.auth.PasswordResetService - Password reset token generated and outbox event dispatched for user id 108642
[Step 3a] Direct SQL query on outbox_events:
  ID: 1556
  EventType: password.reset_requested
  Payload: {"userId": 108642}
  PayloadSecret (Ciphertext): AWEJSQppu6NCL/7BEDfAHQJUi+waNFrkiFzpLXmt8VEja0QLc/TJfO8ckmMuzLEISTYKzme/LicfkOWMs/lCOeEgx4FquOs4/tFKg1bGHwJD9sHs
2026-10-28 21:02:13.179 [main] [NO_TRACE] [ANON] INFO  c.f.messaging.RabbitMqEventPublisher - Successfully published event id=1556 type='password.reset_requested' with broker ACK
2026-10-28 21:02:13.179 [main] [NO_TRACE] [ANON] INFO  c.f.notification.OutboxRelayService - Relayed outbox event id=1556 type='password.reset_requested' successfully
[Step 3b] Post-relay SQL query on outbox_events id=1556:
  Status: SENT
  PayloadSecret (Scrubbed): null
[Step 3c] Password reset email received:
  Subject: Reset your FitFlow password
  To: [dev@fitflow.local]

--- SCENARIO 4: Session Cancellation with 2 Bookings ---
2026-10-28 21:02:13.231 [main] [NO_TRACE] [ANON] INFO  c.fitflow.notification.OutboxService - Appended outbox event id=1557 type='session.cancelled' aggregateType='SESSION' aggregateId=8888
2026-10-28 21:02:13.241 [main] [NO_TRACE] [ANON] INFO  c.f.messaging.RabbitMqEventPublisher - Successfully published event id=1557 type='session.cancelled' with broker ACK
2026-10-28 21:02:13.241 [main] [NO_TRACE] [ANON] INFO  c.f.notification.OutboxRelayService - Relayed outbox event id=1557 type='session.cancelled' successfully
2026-10-28 21:02:13.268 [org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1] [NO_TRACE] [ANON] INFO  c.fitflow.notification.EmailService - Sent email template='session-cancelled' to userId=108642 successfully
2026-10-28 21:02:13.291 [org.springframework.amqp.rabbit.RabbitListenerEndpointContainer#0-1] [NO_TRACE] [ANON] INFO  c.fitflow.notification.EmailService - Sent email template='session-cancelled' to userId=108646 successfully
[Step 4] Session cancelled - 2 emails delivered:
  To: dev@fitflow.local | Subject: Important: Class Session Cancelled
  To: member2_walkthrough@fitflow.local | Subject: Important: Class Session Cancelled

--- SCENARIO 5: Admin Messaging Queues Inspection ---
[Step 5] GET /api/v1/admin/messaging/queues response:
  Queue: q.notifications.email            | Messages: 0    | Consumers: 1
  Queue: q.notifications.email.dlq        | Messages: 0    | Consumers: 0
  Queue: q.agent.events                   | Messages: 2    | Consumers: 0
  Queue: q.agent.events.dlq               | Messages: 0    | Consumers: 0
================================================================================
STAGE 6 LIVE WALKTHROUGH COMPLETED SUCCESSFULLY
================================================================================
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 25.33 s -- in com.fitflow.messaging.Stage6WalkthroughIT
[INFO] BUILD SUCCESS
```

---

## 4. Admin Messaging API & Health Indicator

### 4.1. Queue Inspection API (`GET /api/v1/admin/messaging/queues`)
**HTTP Response (200 OK)**:
```json
{
  "mode": "rabbit",
  "queues": [
    {
      "name": "q.notifications.email",
      "messageCount": 0,
      "consumerCount": 1
    },
    {
      "name": "q.notifications.email.dlq",
      "messageCount": 0,
      "consumerCount": 0
    },
    {
      "name": "q.agent.events",
      "messageCount": 2,
      "consumerCount": 0
    },
    {
      "name": "q.agent.events.dlq",
      "messageCount": 0,
      "consumerCount": 0
    }
  ]
}
```

### 4.2. Outbox Health Indicator (`GET /actuator/health`)
**Component Health Section**:
```json
{
  "status": "UP",
  "components": {
    "outbox": {
      "status": "UP",
      "details": {
        "status": "HEALTHY",
        "pendingCount": 0,
        "failedCount": 0,
        "oldestPendingAgeSeconds": 0
      }
    }
  }
}
```
*Note*: When the oldest pending outbox row exceeds 300 seconds (5 minutes), the component marks its detail status as `DEGRADED`, alerting operations without taking down the application instance.

---

## 5. Test Suite Verification Summary

| Test Class | Category | Tests Run | Pass | Fail | Description |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `com.fitflow.notification.OutboxServiceTest` | Unit | 15 | 15 | 0 | Secret blob encryption, forbidden key rejection (`password`, `token`). |
| `com.fitflow.notification.MoneyFormatterTest` | Unit | 8 | 8 | 0 | Strict non-floating-point ₹ INR currency formatting. |
| `com.fitflow.notification.EmailTemplateTest` | Unit | 38 | 38 | 0 | 19 HTML + 19 TXT Thymeleaf templates rendered without unresolved variables; HTML escaping. |
| `com.fitflow.notification.NotificationRoutingTableTest` | Unit | 19 | 19 | 0 | Verifies all 19 domain events map to exact templates & categories. |
| `com.fitflow.notification.NotificationPreferencesTest` | Unit | 8 | 8 | 0 | Preference flags gating (`BOOKINGS`, `REMINDERS`, `MARKETING`) and `TRANSACTIONAL` bypass. |
| `com.fitflow.notification.EmailRateLimiterTest` | Unit | 3 | 3 | 0 | 20 emails/hour sliding window limit per recipient. |
| `com.fitflow.notification.OutboxRelayTest` | Unit | 5 | 5 | 0 | ID ordering, broker ACK, exponential backoff, FAILED at 10, concurrent `SKIP LOCKED`. |
| `com.fitflow.notification.ConsumersTest` | Unit | 3 | 3 | 0 | Idempotency check against `notification_log`, crash recovery, poison message DLQ rejection. |
| `com.fitflow.notification.ScheduledJobsTest` | Unit | 4 | 4 | 0 | 24h session reminder and 72h membership expiry notice idempotency. |
| `com.fitflow.notification.OutboxSecurityTest` | IT | 2 | 2 | 0 | Logback log-capture proving zero raw tokens/passwords in logs or payloads. |
| `com.fitflow.admin.AdminMessagingControllerIT` | IT | 5 | 5 | 0 | 401/403/200 checks, outbox pagination, retry, queue depths, DLQ replay with 100-limit clamp & audit log. |
| `com.fitflow.notification.OutboxHealthIndicatorTest` | Unit | 2 | 2 | 0 | UP vs DEGRADED health states based on pending row age. |
| `com.fitflow.messaging.RabbitIT` | IT | 2 | 2 | 0 | Live broker publish/consume/ACK and poison message dead-lettering. |
| `com.fitflow.messaging.Stage6WalkthroughIT` | IT | 1 | 1 | 0 | Full 5-scenario live walkthrough. |
| `com.fitflow.architecture.ArchitectureRulesTest` | ArchUnit | 9 | 9 | 0 | Architecture boundary checks across all packages. |
| `com.fitflow.security.EndpointSecurityCoverageTest` | IT | 1 | 1 | 0 | 100% endpoint security coverage gate. |
| **Total Core Stage 6 Tests** | — | **125** | **125** | **0** | **All tests passing green.** |
