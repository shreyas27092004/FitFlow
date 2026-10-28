package com.fitflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "fitflow.messaging.mode", havingValue = "rabbit")
@RequiredArgsConstructor
public class RabbitNotificationConsumer {

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {1000L, 4000L, 16000L};

    private final List<EventHandler> handlers;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();

    @RabbitListener(queues = RabbitMqTopologyConfig.QUEUE_NOTIFICATIONS_EMAIL, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            messageId = "tag-" + deliveryTag;
        }

        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), EventEnvelope.class);
            if (envelope == null || envelope.eventType() == null) {
                log.error("Poison message: missing event envelope or eventType, rejecting to DLQ");
                channel.basicReject(deliveryTag, false);
                return;
            }
        } catch (Exception e) {
            log.error("Poison message: failed to deserialize JSON envelope, rejecting to DLQ: {}", e.getMessage());
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            boolean handled = false;
            for (EventHandler handler : handlers) {
                if (handler.supports(envelope.eventType())) {
                    handler.handle(envelope);
                    handled = true;
                }
            }

            if (!handled) {
                log.warn("Poison message: no handler found for event type {}, rejecting to DLQ", envelope.eventType());
                channel.basicReject(deliveryTag, false);
                return;
            }

            // Success: ack and clean retry tracking
            retryCounts.remove(messageId);
            channel.basicAck(deliveryTag, false);
            log.info("Consumer successfully processed and ACKed messageId={}", messageId);
        } catch (Exception e) {
            int currentRetry = retryCounts.getOrDefault(messageId, 0) + 1;
            retryCounts.put(messageId, currentRetry);

            if (currentRetry > MAX_RETRIES) {
                log.error("Exceeded {} retries for messageId={}, rejecting to DLQ: {}", MAX_RETRIES, messageId, e.getMessage());
                retryCounts.remove(messageId);
                channel.basicReject(deliveryTag, false); // Route to DLQ
            } else {
                long delay = BACKOFF_MS[Math.min(currentRetry - 1, BACKOFF_MS.length - 1)];
                log.warn("Error processing messageId={}, retry {}/{}, backing off {}ms: {}",
                        messageId, currentRetry, MAX_RETRIES, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                // Nack with requeue for retry
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
