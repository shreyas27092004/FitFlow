package com.fitflow.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.messaging.EventEnvelope;
import com.fitflow.messaging.EventPublisher;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    private Counter publishedCounter;
    private Counter failedCounter;

    @PostConstruct
    public void initCounters() {
        publishedCounter = meterRegistry.counter("outbox.published");
        failedCounter = meterRegistry.counter("outbox.failed");
    }

    @Scheduled(fixedRateString = "${fitflow.outbox.poll-interval-ms:1000}")
    public void runScheduledRelay() {
        try {
            pollAndRelayBatch(50);
        } catch (Exception e) {
            log.error("Outbox relay scheduled run encountered an error: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int pollAndRelayBatch(int limit) {
        Instant now = clock.instant();
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingForRelay(now, limit);

        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Found {} pending outbox events for relay", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                Map<String, Object> payloadMap;
                if (event.getPayload() != null && !event.getPayload().isBlank()) {
                    payloadMap = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
                } else {
                    payloadMap = Collections.emptyMap();
                }

                EventEnvelope envelope = new EventEnvelope(
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getCreatedAt(),
                        payloadMap,
                        event.getPayloadSecret()
                );

                eventPublisher.publish(envelope);

                // On broker confirm success:
                event.setStatus(OutboxEvent.Status.SENT);
                event.setSentAt(clock.instant());
                event.setPayloadSecret(null); // Scrub secret from DB on SENT
                event.setLastError(null);

                if (publishedCounter != null) {
                    publishedCounter.increment();
                }
                log.info("Relayed outbox event id={} type='{}' successfully", event.getId(), event.getEventType());
            } catch (Exception e) {
                int attempts = (event.getAttempts() != null ? event.getAttempts() : 0) + 1;
                event.setAttempts(attempts);

                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                if (errorMsg.length() > 290) {
                    errorMsg = errorMsg.substring(0, 290);
                }
                event.setLastError(errorMsg);

                if (attempts >= 10) {
                    event.setStatus(OutboxEvent.Status.FAILED);
                    log.error("Outbox event id={} marked FAILED after {} attempts: {}", event.getId(), attempts, errorMsg);
                } else {
                    long backoffSeconds = Math.min((long) Math.pow(2, attempts), 300L);
                    Instant nextAttempt = clock.instant().plusSeconds(backoffSeconds);
                    event.setNextAttemptAt(nextAttempt);
                    log.warn("Relay failed for outbox event id={} attempt {}/10, next attempt at {}: {}",
                            event.getId(), attempts, nextAttempt, errorMsg);
                }

                if (failedCounter != null) {
                    failedCounter.increment();
                }
            }
        }

        outboxEventRepository.saveAll(pendingEvents);
        return pendingEvents.size();
    }
}
