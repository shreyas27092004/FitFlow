package com.fitflow.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class RabbitMqEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(EventEnvelope envelope) {
        String eventType = envelope.eventType();
        CorrelationData correlationData = new CorrelationData(String.valueOf(envelope.eventId()));

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqTopologyConfig.EXCHANGE_EVENTS,
                    eventType,
                    envelope,
                    message -> {
                        message.getMessageProperties().setMessageId(String.valueOf(envelope.eventId()));
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setContentType("application/json");
                        return message;
                    },
                    correlationData
            );

            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm != null ? confirm.getReason() : "No confirmation received within timeout";
                throw new RuntimeException("RabbitMQ broker NACKed or rejected message " + envelope.eventId() + ": " + reason);
            }

            log.info("Successfully published event id={} type='{}' with broker ACK", envelope.eventId(), eventType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Publisher confirm interrupted for event " + envelope.eventId(), e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Publisher confirm timed out after 5s for event " + envelope.eventId(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event " + envelope.eventId() + " to RabbitMQ: " + e.getMessage(), e);
        }
    }
}
