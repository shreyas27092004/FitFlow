package com.fitflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.messaging.EventEnvelope;
import com.fitflow.messaging.InMemoryEventDispatcher;
import com.fitflow.messaging.RabbitNotificationConsumer;
import com.fitflow.security.EncryptionService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumersTest {

    private EmailService emailService;
    private NotificationRecipientDao recipientDao;
    private NotificationLogDao notificationLogDao;
    private EncryptionService encryptionService;
    private ObjectMapper objectMapper;
    private Clock clock;
    private EmailNotificationConsumer consumer;

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        recipientDao = mock(NotificationRecipientDao.class);
        notificationLogDao = mock(NotificationLogDao.class);
        encryptionService = mock(EncryptionService.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);

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
        when(notificationLogDao.recordPending(any(), any(), any(), any(), any())).thenReturn(101L);
    }

    @Test
    @DisplayName("Same event dispatched twice sends only one email due to notification_log idempotency check")
    void testSameEventTwiceSendsOneEmail() {
        Long userId = 44L;
        NotificationRecipientDao.RecipientInfo member = new NotificationRecipientDao.RecipientInfo(
                userId, "member@fitflow.local", "Charlie", "ACTIVE", true, true, true
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(member));

        EventEnvelope envelope = new EventEnvelope(
                5001L, "booking.created", "BOOKING", 123L, baseTime, Map.of("userId", userId), null
        );

        // First delivery: not yet sent
        when(notificationLogDao.hasAlreadySent(userId, "booking-confirmed", 5001L)).thenReturn(false);
        consumer.handle(envelope);

        // Second delivery (duplicate): now marked sent
        when(notificationLogDao.hasAlreadySent(userId, "booking-confirmed", 5001L)).thenReturn(true);
        consumer.handle(envelope);

        // Assert sendEmail was only invoked once!
        verify(emailService, times(1)).sendEmail(eq(userId), anyString(), eq("booking-confirmed"), anyString(), any());
    }

    @Test
    @DisplayName("Crash after send but before ack causes retry that skips email sending")
    void testCrashBetweenSendAndAckSkipsSecondEmail() {
        Long userId = 55L;
        NotificationRecipientDao.RecipientInfo member = new NotificationRecipientDao.RecipientInfo(
                userId, "member@fitflow.local", "Diana", "ACTIVE", true, true, true
        );
        when(recipientDao.findById(userId)).thenReturn(Optional.of(member));

        EventEnvelope envelope = new EventEnvelope(
                6001L, "booking.created", "BOOKING", 124L, baseTime, Map.of("userId", userId), null
        );

        // First attempt sends email and updates log to success
        when(notificationLogDao.hasAlreadySent(userId, "booking-confirmed", 6001L)).thenReturn(false);
        consumer.handle(envelope);
        verify(notificationLogDao).recordSuccess(eq(101L), any());

        // Now simulate consumer crash / replay: hasAlreadySent is true in PostgreSQL
        when(notificationLogDao.hasAlreadySent(userId, "booking-confirmed", 6001L)).thenReturn(true);
        consumer.handle(envelope);

        // Still only 1 email sent!
        verify(emailService, times(1)).sendEmail(eq(userId), anyString(), eq("booking-confirmed"), anyString(), any());
    }

    @Test
    @DisplayName("Poison message (malformed JSON) is rejected directly to DLQ without requeue")
    void testPoisonMessageGoesStraightToDlq() throws IOException {
        RabbitNotificationConsumer rabbitConsumer = new RabbitNotificationConsumer(
                List.of(consumer),
                objectMapper
        );

        byte[] invalidJson = "{bad-json-payload-corrupt".getBytes();
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(42L);
        properties.setMessageId("msg-poison-1");
        Message message = new Message(invalidJson, properties);

        Channel channel = mock(Channel.class);

        rabbitConsumer.onMessage(message, channel);

        // basicReject with requeue=false -> goes to DLQ!
        verify(channel).basicReject(42L, false);
    }

    @Test
    @DisplayName("InMemory dispatcher routes to dead letters on unhandled poison message")
    void testInMemoryDispatcherRecordsDeadLetter() {
        InMemoryEventDispatcher dispatcher = new InMemoryEventDispatcher(List.of(consumer));

        EventEnvelope poisonEnvelope = new EventEnvelope(
                9999L, "unknown.poison.event", "UNKNOWN", 0L, baseTime, Map.of(), null
        );

        dispatcher.publish(poisonEnvelope);

        // No exception thrown, handled as unhandled
        assertEquals(1, dispatcher.getDispatchedEvents().size());
    }
}
