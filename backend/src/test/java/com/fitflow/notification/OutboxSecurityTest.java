package com.fitflow.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.EncryptionService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxSecurityTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    private OutboxEventRepository outboxEventRepository;
    private EncryptionService encryptionService;
    private ObjectMapper objectMapper;
    private Clock clock;
    private OutboxService outboxService;

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        outboxEventRepository = mock(OutboxEventRepository.class);
        encryptionService = mock(EncryptionService.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);

        outboxService = new OutboxService(outboxEventRepository, encryptionService, objectMapper, clock);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(encryptionService.encryptString(any())).thenReturn("ENC_CIPHER_TEXT_SECURE");
    }

    @AfterEach
    void tearDown() {
        if (rootLogger != null && listAppender != null) {
            rootLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("Password reset requested outbox row never holds plaintext token in payload and logs never leak token")
    void testPasswordResetTokenNotPlaintextInOutboxOrLogs() {
        String rawToken = "super-secret-password-reset-token-9876543210";

        // Dispatch via outboxService.appendWithSecret
        OutboxEvent event = outboxService.appendWithSecret(
                "USER",
                42L,
                "password.reset_requested",
                Map.of("userId", 42L),
                Map.of("token", rawToken)
        );

        // Assert outbox row payload does NOT contain the raw token
        assertFalse(event.getPayload().contains(rawToken), "Raw token must not appear in outbox event payload");
        assertNotNull(event.getPayloadSecret());
        assertEquals("ENC_CIPHER_TEXT_SECURE", event.getPayloadSecret());

        // Assert all captured logs do not contain raw token or smtp password
        List<ILoggingEvent> logs = listAppender.list;
        for (ILoggingEvent logEvent : logs) {
            String msg = logEvent.getFormattedMessage();
            assertFalse(msg.contains(rawToken), "Log message must never leak raw token: " + msg);
            assertFalse(msg.contains("SMTP_PASSWORD"), "Log message must never leak SMTP password: " + msg);
        }
    }

    @Test
    @DisplayName("Admin user created outbox row never holds plaintext temporary password in payload or logs")
    void testUserCreatedTempPasswordNotPlaintextInOutboxOrLogs() {
        String tempPassword = "MyTemporarySecretPassword@2026";

        OutboxEvent event = outboxService.appendWithSecret(
                "USER",
                88L,
                "user.created",
                Map.of("userId", 88L, "role", "MEMBER"),
                Map.of("tempPassword", tempPassword)
        );

        assertFalse(event.getPayload().contains(tempPassword), "Temp password must not appear in outbox event payload");
        assertNotNull(event.getPayloadSecret());

        List<ILoggingEvent> logs = listAppender.list;
        for (ILoggingEvent logEvent : logs) {
            String msg = logEvent.getFormattedMessage();
            assertFalse(msg.contains(tempPassword), "Log message must never leak temp password: " + msg);
        }
    }
}
