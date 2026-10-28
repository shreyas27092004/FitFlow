package com.fitflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.messaging.EventEnvelope;
import com.fitflow.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferencesTest {

    private EmailService emailService;
    private NotificationRecipientDao recipientDao;
    private NotificationLogDao notificationLogDao;
    private EncryptionService encryptionService;
    private ObjectMapper objectMapper;
    private Clock clock;
    private EmailNotificationConsumer consumer;

    private final Instant fixedTime = Instant.parse("2026-10-28T12:00:00Z");

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        recipientDao = mock(NotificationRecipientDao.class);
        notificationLogDao = mock(NotificationLogDao.class);
        encryptionService = mock(EncryptionService.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(fixedTime, ZoneOffset.UTC);

        consumer = new EmailNotificationConsumer(
                emailService,
                recipientDao,
                notificationLogDao,
                encryptionService,
                objectMapper,
                clock
        );

        when(emailService.getFrontendBaseUrl()).thenReturn("http://localhost:3000");
        when(emailService.formatInstant(any())).thenReturn("28 Oct 2026, 12:00 PM UTC");
        when(emailService.sendEmail(any(), any(), any(), any(), any())).thenReturn(true);
        when(notificationLogDao.recordPending(any(), any(), any(), any(), any())).thenReturn(100L);
    }

    @Test
    @DisplayName("BOOKINGS category email is skipped when user disabled emailBookings")
    void testBookingsCategorySkippedWhenDisabled() {
        Long userId = 10L;
        NotificationRecipientDao.RecipientInfo recipient = new NotificationRecipientDao.RecipientInfo(
                userId, "user@fitflow.local", "Alice", "ACTIVE", false, true, true
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(recipient));

        EventEnvelope envelope = new EventEnvelope(
                1L, "booking.created", "BOOKING", 100L, fixedTime, Map.of("userId", userId), null
        );

        consumer.handle(envelope);

        verify(emailService, never()).sendEmail(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("REMINDERS category email is skipped when user disabled emailReminders")
    void testRemindersCategorySkippedWhenDisabled() {
        Long userId = 10L;
        NotificationRecipientDao.RecipientInfo recipient = new NotificationRecipientDao.RecipientInfo(
                userId, "user@fitflow.local", "Alice", "ACTIVE", true, false, true
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(recipient));

        EventEnvelope envelope = new EventEnvelope(
                1L, "session.reminder", "BOOKING", 100L, fixedTime, Map.of("userId", userId), null
        );

        consumer.handle(envelope);

        verify(emailService, never()).sendEmail(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("TRANSACTIONAL category email is always sent even if user disabled all preferences")
    void testTransactionalCategoryAlwaysSent() {
        Long userId = 10L;
        NotificationRecipientDao.RecipientInfo recipient = new NotificationRecipientDao.RecipientInfo(
                userId, "user@fitflow.local", "Alice", "ACTIVE", false, false, false
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(recipient));

        EventEnvelope envelope = new EventEnvelope(
                1L, "membership.started", "MEMBERSHIP", 100L, fixedTime, Map.of("userId", userId), null
        );

        consumer.handle(envelope);

        verify(emailService).sendEmail(any(), anyString(), eq("membership-started"), anyString(), any());
    }

    @Test
    @DisplayName("DELETED users are skipped from receiving emails")
    void testDeletedUserSkipped() {
        Long userId = 10L;
        NotificationRecipientDao.RecipientInfo recipient = new NotificationRecipientDao.RecipientInfo(
                userId, "user@fitflow.local", "Alice", "DELETED", true, true, true
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(recipient));

        EventEnvelope envelope = new EventEnvelope(
                1L, "membership.started", "MEMBERSHIP", 100L, fixedTime, Map.of("userId", userId), null
        );

        consumer.handle(envelope);

        verify(emailService, never()).sendEmail(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("SUSPENDED users are skipped for normal emails but receive the suspension notice")
    void testSuspendedUserHandling() {
        Long userId = 10L;
        NotificationRecipientDao.RecipientInfo recipient = new NotificationRecipientDao.RecipientInfo(
                userId, "user@fitflow.local", "Alice", "SUSPENDED", true, true, true
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(recipient));

        // Normal event -> skipped
        EventEnvelope normalEvent = new EventEnvelope(
                1L, "membership.started", "MEMBERSHIP", 100L, fixedTime, Map.of("userId", userId), null
        );
        consumer.handle(normalEvent);
        verify(emailService, never()).sendEmail(any(), anyString(), eq("membership-started"), anyString(), any());

        // Suspension notice event -> SENT!
        EventEnvelope suspendEvent = new EventEnvelope(
                2L, "user.suspended", "USER", userId, fixedTime, Map.of("userId", userId), null
        );
        consumer.handle(suspendEvent);
        verify(emailService).sendEmail(any(), anyString(), eq("account-suspended"), anyString(), any());
    }
}
