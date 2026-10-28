package com.fitflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.devtools.DevMailServer;
import com.fitflow.notification.EmailNotificationConsumer;
import com.fitflow.notification.NotificationRecipientDao;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.icegreen.greenmail.util.GreenMail;
import com.rabbitmq.client.Channel;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"test", "dev"})
public class RabbitIT {

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

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;

    @Autowired(required = false)
    private DevMailServer devMailServer;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("RabbitIT: publish -> RabbitMQ topic exchange -> queue -> consumer -> GreenMail mail")
    void testRabbitMqPublishAndConsume() throws Exception {
        Assumptions.assumeTrue(RABBIT_REACHABLE, "RabbitMQ broker is not reachable on localhost:5672; reported as UNVERIFIED");
        assertNotNull(rabbitTemplate, "RabbitTemplate must be present in rabbit mode");

        if (devMailServer != null) {
            devMailServer.reset();
        }
        GreenMail greenMail = devMailServer != null ? devMailServer.getGreenMail() : null;

        if (rabbitAdmin != null) {
            try {
                rabbitAdmin.purgeQueue(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL, true);
                rabbitAdmin.purgeQueue(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL_DLQ, true);
            } catch (Exception ignored) {}
        }

        User rabbitUser = userRepository.findByEmail("rabbit_member@fitflow.local")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("rabbit_member@fitflow.local")
                        .fullName("Rabbit User")
                        .passwordHash("hash")
                        .status(User.Status.ACTIVE)
                        .notificationPrefs("{\"emailBookings\":true,\"emailReminders\":true,\"emailMarketing\":true}")
                        .build()));
        Long testMemberId = rabbitUser.getId();

        long uniqueEventId = System.currentTimeMillis();
        EventEnvelope envelope = new EventEnvelope(
                uniqueEventId,
                "booking.created",
                "BOOKING",
                555L,
                Instant.parse("2026-10-28T10:00:00Z"),
                Map.of(
                        "userId", testMemberId,
                        "className", "RabbitMQ Power Spin",
                        "sessionStartTime", "28 Oct 2026 10:00 AM IST",
                        "trainerName", "Speedy"
                ),
                null
        );

        RabbitMqEventPublisher publisher = new RabbitMqEventPublisher(rabbitTemplate);
        publisher.publish(envelope);

        if (greenMail != null) {
            boolean received = greenMail.waitForIncomingEmail(25000, 1);
            assertTrue(received, "GreenMail should receive booking confirmation email within 25s");
            MimeMessage[] messages = greenMail.getReceivedMessages();
            assertTrue(messages.length >= 1);
            assertEquals("Booking Confirmed", messages[0].getSubject());
        }
    }

    @Test
    @DisplayName("RabbitIT: poison message routes to DLQ path")
    void testPoisonMessageRoutesToDlq() throws Exception {
        Assumptions.assumeTrue(RABBIT_REACHABLE, "RabbitMQ broker is not reachable on localhost:5672; reported as UNVERIFIED");
        assertNotNull(rabbitTemplate, "RabbitTemplate must be present in rabbit mode");

        // Send raw bad message directly to q.notifications.email
        byte[] malformedBytes = "{bad-json".getBytes();
        rabbitTemplate.send("", RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL, new org.springframework.amqp.core.Message(malformedBytes));

        // Sleep to let consumer reject to DLQ
        Thread.sleep(1500);

        // Check DLQ depth or receive from DLQ
        org.springframework.amqp.core.Message dlqMsg = rabbitTemplate.receive(RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL_DLQ, 5000);
        assertNotNull(dlqMsg, "Dead-letter queue should receive the rejected poison message");
    }
}
