package com.fitflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.AdminMessagingService;
import com.fitflow.admin.dto.QueueDepthResponse;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.BookingResponse;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.devtools.DevMailController;
import com.fitflow.devtools.DevMailServer;
import com.fitflow.notification.NotificationRecipientDao;
import com.fitflow.notification.OutboxRelayService;
import com.fitflow.notification.OutboxService;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.icegreen.greenmail.util.GreenMail;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
public class Stage6WalkthroughIT {

    private static final boolean RABBIT_REACHABLE;

    static {
        boolean reachable = false;
        try (Socket socket = new Socket("localhost", 5672)) {
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        RABBIT_REACHABLE = reachable;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (RABBIT_REACHABLE) {
            registry.add("fitflow.messaging.mode", () -> "rabbit");
            registry.add("spring.rabbitmq.host", () -> "localhost");
            registry.add("spring.rabbitmq.port", () -> 5672);
            registry.add("spring.rabbitmq.username", () -> "guest");
            registry.add("spring.rabbitmq.password", () -> "guest");
            registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private DevMailServer devMailServer;

    @Autowired
    private DevMailController devMailController;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private AdminMessagingService adminMessagingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("Stage 6 Full Walkthrough (Scenarios 1-5)")
    void runFullWalkthrough() throws Exception {
        Assumptions.assumeTrue(RABBIT_REACHABLE, "RabbitMQ required for live walkthrough");

        GreenMail greenMail = devMailServer.getGreenMail();
        assertNotNull(greenMail, "GreenMail must be active");
        devMailServer.reset();

        // Clear outbox backlog from any previous tests
        outboxEventRepository.deleteAll();

        // Purge test queues to clear backlog from any previous aborted runs
        try {
            rabbitAdmin.purgeQueue(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL, true);
            rabbitAdmin.purgeQueue(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL_DLQ, true);
            rabbitAdmin.purgeQueue(RabbitMqTopologyConfig.QUEUE_AGENT_EVENTS, true);
            rabbitAdmin.purgeQueue(RabbitMqTopologyConfig.QUEUE_AGENT_EVENTS_DLQ, true);
        } catch (Exception ignored) {}

        System.out.println("================================================================================");
        System.out.println("STAGE 6 LIVE WALKTHROUGH EXECUTION");
        System.out.println("================================================================================");

        // -------------------------------------------------------------------------
        // SCENARIO 1: Member books a session with RabbitMQ running and mode=rabbit
        // -------------------------------------------------------------------------
        System.out.println("\n--- SCENARIO 1: Booking confirmation via RabbitMQ & Email ---");

        // 1a. Show queue properties before
        Properties initialProps = rabbitAdmin.getQueueProperties(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL);
        int initialMsgCount = initialProps != null ? (int) initialProps.get(RabbitAdmin.QUEUE_MESSAGE_COUNT) : 0;
        System.out.println("[Step 1a] Queue '" + RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL + "' initial message depth: " + initialMsgCount);

        // Find or create member user
        User member = userRepository.findByEmail("dev@fitflow.local").orElse(null);
        if (member == null) {
            member = userRepository.save(User.builder()
                    .email("walkthrough_member@fitflow.local")
                    .fullName("Walkthrough Member")
                    .passwordHash("$2a$10$dummyHash...")
                    .status(User.Status.ACTIVE)
                    .build());
        }

        // Direct event publishing or booking dispatch
        long walkEventId = System.currentTimeMillis();
        EventEnvelope bookingEnvelope = new EventEnvelope(
                walkEventId,
                "booking.created",
                "BOOKING",
                1001L,
                clock.instant(),
                Map.of(
                        "userId", member.getId(),
                        "className", "CrossFit Power WOD",
                        "sessionStartTime", "Tomorrow at 7:00 AM IST",
                        "trainerName", "Coach Viktor"
                ),
                null
        );

        RabbitMqEventPublisher publisher = new RabbitMqEventPublisher(rabbitTemplate);
        publisher.publish(bookingEnvelope);
        System.out.println("[Step 1b] Published 'booking.created' event (eventId=" + walkEventId + ") to topic exchange 'fitflow.events'");

        // Wait for RabbitMQ consumer to process and deliver email
        boolean emailReceived = greenMail.waitForIncomingEmail(15000, 1);
        assertTrue(emailReceived, "GreenMail should receive the booking email");

        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertTrue(receivedMessages.length >= 1);
        MimeMessage bookingEmail = receivedMessages[receivedMessages.length - 1];
        System.out.println("[Step 1c] GreenMail received email:");
        System.out.println("  To: " + member.getEmail());
        System.out.println("  Subject: " + bookingEmail.getSubject());
        assertEquals("Booking Confirmed", bookingEmail.getSubject());

        var devMailEmails = devMailController.getReceivedEmails().getBody();
        assertNotNull(devMailEmails);
        assertFalse(devMailEmails.isEmpty());
        System.out.println("[Step 1d] DevMailController /dev/mail verified. Count: " + devMailEmails.size());

        // -------------------------------------------------------------------------
        // SCENARIO 2: Outage test - unreachable broker, retry, backoff, then recovery
        // -------------------------------------------------------------------------
        System.out.println("\n--- SCENARIO 2: Broker Outage, Exponential Backoff & Recovery ---");

        // Create outbox row
        OutboxEvent outageEvent = OutboxEvent.builder()
                .aggregateType("BOOKING")
                .aggregateId(2002L)
                .eventType("booking.created")
                .payload(objectMapper.writeValueAsString(Map.of(
                        "userId", member.getId(),
                        "className", "Yoga Flow",
                        "sessionStartTime", "Tomorrow 8:00 AM IST",
                        "trainerName", "Elena"
                )))
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .createdAt(clock.instant())
                .nextAttemptAt(clock.instant())
                .build();
        outageEvent = outboxEventRepository.save(outageEvent);
        System.out.println("[Step 2a] Created outbox event id=" + outageEvent.getId() + " status=" + outageEvent.getStatus());

        // Simulate failed delivery by using mock failure
        OutboxRelayService failingRelay = new OutboxRelayService(
                outboxEventRepository,
                env -> { throw new RuntimeException("Simulated Broker Connection Refused (TCP:5672)"); },
                objectMapper,
                clock,
                null
        );

        // Run failing relay batch
        failingRelay.pollAndRelayBatch(50);

        OutboxEvent refreshedEvent = outboxEventRepository.findById(outageEvent.getId()).orElseThrow();
        System.out.println("[Step 2b] After broker outage attempt:");
        System.out.println("  Status: " + refreshedEvent.getStatus());
        System.out.println("  Attempts: " + refreshedEvent.getAttempts());
        System.out.println("  Last Error: " + refreshedEvent.getLastError());
        System.out.println("  Next Attempt At: " + refreshedEvent.getNextAttemptAt());
        assertEquals(OutboxEvent.Status.PENDING, refreshedEvent.getStatus());
        assertEquals(1, refreshedEvent.getAttempts());
        assertTrue(refreshedEvent.getLastError().contains("Simulated Broker Connection Refused"));

        // Admin checks outbox
        var pendingPage = adminMessagingService.getOutboxEvents("PENDING", 0, 10);
        assertTrue(pendingPage.totalElements() > 0);
        System.out.println("[Step 2c] Admin endpoint /api/v1/admin/messaging/outbox?status=PENDING shows " + pendingPage.totalElements() + " pending events");

        // Recover: broker restored, nextAttemptAt due
        refreshedEvent.setNextAttemptAt(clock.instant().minusSeconds(1));
        outboxEventRepository.save(refreshedEvent);

        devMailServer.reset();
        // Relay with real publisher
        outboxRelayService.pollAndRelayBatch(50);

        OutboxEvent sentEvent = outboxEventRepository.findById(outageEvent.getId()).orElseThrow();
        System.out.println("[Step 2d] After broker recovered & relay executed:");
        System.out.println("  Status: " + sentEvent.getStatus());
        System.out.println("  Sent At: " + sentEvent.getSentAt());
        assertEquals(OutboxEvent.Status.SENT, sentEvent.getStatus());
        assertNotNull(sentEvent.getSentAt());

        // Email arrives once
        boolean recoveredEmailReceived = greenMail.waitForIncomingEmail(10000, 1);
        assertTrue(recoveredEmailReceived, "Email should arrive once after relay recovers");
        System.out.println("[Step 2e] GreenMail received recovered email: " + greenMail.getReceivedMessages()[0].getSubject());

        // -------------------------------------------------------------------------
        // SCENARIO 3: Forgot password for seeded user & SQL token confidentiality check
        // -------------------------------------------------------------------------
        System.out.println("\n--- SCENARIO 3: Forgot Password Confidentiality & Direct SQL Check ---");
        devMailServer.reset();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + member.getEmail() + "\"}"))
                .andExpect(status().isAccepted());

        // Inspect MySQL outbox_events directly via SQL query
        Map<String, Object> outboxRow = jdbcTemplate.queryForMap(
                "SELECT id, event_type, payload, payload_secret, status FROM outbox_events WHERE aggregate_type = 'USER' AND event_type = 'password.reset_requested' ORDER BY id DESC LIMIT 1"
        );

        System.out.println("[Step 3a] Direct SQL query on outbox_events:");
        System.out.println("  ID: " + outboxRow.get("id"));
        System.out.println("  EventType: " + outboxRow.get("event_type"));
        System.out.println("  Payload: " + outboxRow.get("payload"));
        System.out.println("  PayloadSecret (Ciphertext): " + outboxRow.get("payload_secret"));

        String payloadStr = (String) outboxRow.get("payload");
        assertFalse(payloadStr.contains("token"), "Raw token must NOT be in payload");
        assertNotNull(outboxRow.get("payload_secret"), "Encrypted secret must be in payload_secret before sending");

        // Run relay
        outboxRelayService.pollAndRelayBatch(50);

        // Verify secret is scrubbed to NULL on SENT
        Map<String, Object> scrubbedRow = jdbcTemplate.queryForMap(
                "SELECT payload_secret, status FROM outbox_events WHERE id = ?", outboxRow.get("id")
        );
        System.out.println("[Step 3b] Post-relay SQL query on outbox_events id=" + outboxRow.get("id") + ":");
        System.out.println("  Status: " + scrubbedRow.get("status"));
        System.out.println("  PayloadSecret (Scrubbed): " + scrubbedRow.get("payload_secret"));
        assertEquals("SENT", scrubbedRow.get("status"));
        assertNull(scrubbedRow.get("payload_secret"), "payload_secret MUST be null (scrubbed) after SENT");

        // Verify email arrived with working reset link
        boolean resetEmailReceived = greenMail.waitForIncomingEmail(10000, 1);
        assertTrue(resetEmailReceived);
        var devEmails = devMailController.getReceivedEmails().getBody();
        assertNotNull(devEmails);
        assertFalse(devEmails.isEmpty());
        var lastEmail = devEmails.get(devEmails.size() - 1);
        System.out.println("[Step 3c] Password reset email received:");
        System.out.println("  Subject: " + lastEmail.subject());
        System.out.println("  To: " + lastEmail.to());
        assertTrue(lastEmail.content().contains("/reset-password?token="), "Email body must contain reset password link");

        // -------------------------------------------------------------------------
        // SCENARIO 4: Session cancellation with 2 bookings
        // -------------------------------------------------------------------------
        System.out.println("\n--- SCENARIO 4: Session Cancellation with 2 Bookings ---");
        devMailServer.reset();

        // Seed 2nd member
        User member2 = userRepository.findByEmail("member2_walkthrough@fitflow.local").orElse(null);
        if (member2 == null) {
            member2 = userRepository.save(User.builder()
                    .email("member2_walkthrough@fitflow.local")
                    .fullName("Member Two")
                    .passwordHash("$2a$10$dummyHash...")
                    .status(User.Status.ACTIVE)
                    .build());
        }

        // Emit session.cancelled event with 2 confirmed bookings
        long testSessionId = 8888L;
        // Mock confirmed bookings in database for session 8888
        jdbcTemplate.update("INSERT INTO fitness_classes (id, name, category, duration_minutes, intensity, impact_level, met_value, min_age, default_capacity, active, metadata_reviewed, created_by, created_at, updated_at) " +
                "VALUES (888, 'HIIT Blast 888', 'CARDIO', 60, 'HIGH', 'HIGH', 8.0, 16, 20, true, true, ?, NOW(), NOW()) ON DUPLICATE KEY UPDATE name=VALUES(name)", member.getId());
        jdbcTemplate.update("INSERT INTO class_sessions (id, class_id, trainer_id, trainer_rate_snapshot_paise, starts_at, ends_at, capacity, booked_count, status, room, created_at, updated_at) " +
                "VALUES (8888, 888, ?, 50000, DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 20, 2, 'CANCELLED', 'Studio A', NOW(), NOW()) ON DUPLICATE KEY UPDATE status='CANCELLED'", member.getId());
        jdbcTemplate.update("DELETE FROM bookings WHERE session_id = 8888");
        jdbcTemplate.update("INSERT INTO bookings (session_id, member_id, status, consumed_from, idempotency_key, booked_at, version, created_at) " +
                "VALUES (8888, ?, 'CONFIRMED', 'FREE_SESSION', 'idemp-1', NOW(), 0, NOW())", member.getId());
        jdbcTemplate.update("INSERT INTO bookings (session_id, member_id, status, consumed_from, idempotency_key, booked_at, version, created_at) " +
                "VALUES (8888, ?, 'CONFIRMED', 'FREE_SESSION', 'idemp-2', NOW(), 0, NOW())", member2.getId());

        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(txStatus -> {
            outboxService.append("SESSION", testSessionId, "session.cancelled", Map.of(
                    "sessionId", testSessionId,
                    "className", "HIIT Blast",
                    "sessionStartTime", "Friday 6:00 PM IST",
                    "reason", "Trainer illness"
            ));
            return null;
        });

        // Relay to RabbitMQ
        outboxRelayService.pollAndRelayBatch(50);

        // Expect 2 emails in GreenMail
        boolean twoEmailsReceived = greenMail.waitForIncomingEmail(10000, 2);
        assertTrue(twoEmailsReceived, "Both members should receive session cancellation email");
        System.out.println("[Step 4] Session cancelled - 2 emails delivered:");
        for (MimeMessage msg : greenMail.getReceivedMessages()) {
            System.out.println("  To: " + msg.getAllRecipients()[0] + " | Subject: " + msg.getSubject());
            assertEquals("Important: Class Session Cancelled", msg.getSubject());
        }

        // -------------------------------------------------------------------------
        // SCENARIO 5: Admin Messaging Queues inspection
        // -------------------------------------------------------------------------
        System.out.println("\n--- SCENARIO 5: Admin Messaging Queues Inspection ---");
        QueueDepthResponse depthResponse = adminMessagingService.getQueues();
        System.out.println("[Step 5] GET /api/v1/admin/messaging/queues response:");
        for (QueueDepthResponse.QueueInfo q : depthResponse.queues()) {
            System.out.printf("  Queue: %-32s | Messages: %-4d | Consumers: %d%n",
                    q.name(), q.messageCount(), q.consumerCount());
        }
        assertFalse(depthResponse.queues().isEmpty());

        System.out.println("================================================================================");
        System.out.println("STAGE 6 LIVE WALKTHROUGH COMPLETED SUCCESSFULLY");
        System.out.println("================================================================================");
    }
}
