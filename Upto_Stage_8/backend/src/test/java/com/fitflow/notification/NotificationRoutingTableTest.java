package com.fitflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.messaging.EventEnvelope;
import com.fitflow.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRoutingTableTest {

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

    @ParameterizedTest(name = "Event {0} -> Template {1}")
    @CsvSource({
            "user.created, welcome-created",
            "password.reset_requested, password-reset",
            "user.suspended, account-suspended",
            "booking.created, booking-confirmed",
            "booking.cancelled, booking-cancelled",
            "waitlist.promoted, waitlist-promoted",
            "session.cancelled, session-cancelled",
            "session.rescheduled, session-rescheduled",
            "session.reminder, session-reminder",
            "membership.started, membership-started",
            "membership.changed, membership-changed",
            "membership.expiring, membership-expiring",
            "membership.expired, membership-expired",
            "payment.succeeded, payment-receipt",
            "payment.failed, payment-failed",
            "refund.pending_approval, refund-needs-approval",
            "refund.issued, refund-issued",
            "payout.approved, payout-approved",
            "feedback.pain_reported, trainer-pain-flag"
    })
    @DisplayName("Verify complete Notification Table event -> template mapping")
    void testEventRouting(String eventType, String expectedTemplate) {
        Long memberId = 10L;
        NotificationRecipientDao.RecipientInfo memberInfo = new NotificationRecipientDao.RecipientInfo(
                memberId, "member@fitflow.local", "Bob", "ACTIVE", true, true, true
        );

        when(recipientDao.findById(memberId)).thenReturn(Optional.of(memberInfo));
        when(recipientDao.findConfirmedBookingUserIdsForSession(any())).thenReturn(List.of(memberId));
        when(recipientDao.findTrainerUserIdForSession(any())).thenReturn(Optional.of(memberId));
        when(recipientDao.findAdminsWithPermission("REFUND_APPROVE")).thenReturn(List.of(memberInfo));

        Map<String, Object> payload = Map.of(
                "userId", memberId,
                "sessionId", 55L,
                "trainerUserId", memberId,
                "amountPaise", 49900L
        );

        EventEnvelope envelope = new EventEnvelope(
                1L, eventType, "AGGREGATE", memberId, fixedTime, payload, null
        );

        consumer.handle(envelope);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(any(), anyString(), templateCaptor.capture(), anyString(), any());

        assertEquals(expectedTemplate, templateCaptor.getValue());
    }
}
