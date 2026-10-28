package com.fitflow.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payloadMap) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw ApiException.internalError("Failed to serialize outbox event payload: " + eventType);
        }

        Instant now = clock.instant();
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);
        log.info("Appended outbox event id={} type='{}' aggregateType='{}' aggregateId={}",
                saved.getId(), eventType, aggregateType, aggregateId);
        return saved;
    }
}
