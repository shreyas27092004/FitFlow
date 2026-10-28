package com.fitflow.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final Set<String> FORBIDDEN_PAYLOAD_KEYS = Set.of(
            "password",
            "token",
            "temppassword",
            "secret",
            "apikey"
    );

    private final OutboxEventRepository outboxEventRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payloadMap) {
        return appendWithSecret(aggregateType, aggregateId, eventType, payloadMap, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent appendWithSecret(
            String aggregateType,
            Long aggregateId,
            String eventType,
            Map<String, Object> payloadMap,
            Map<String, Object> secretMap) {

        validatePayloadKeys(payloadMap);

        Map<String, Object> safePayload = payloadMap != null ? payloadMap : Collections.emptyMap();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(safePayload);
        } catch (JsonProcessingException e) {
            throw ApiException.internalError("Failed to serialize outbox event payload: " + eventType);
        }

        String encryptedSecret = null;
        if (secretMap != null && !secretMap.isEmpty()) {
            try {
                String secretJson = objectMapper.writeValueAsString(secretMap);
                encryptedSecret = encryptionService.encryptString(secretJson);
            } catch (JsonProcessingException e) {
                throw ApiException.internalError("Failed to serialize outbox event secret: " + eventType);
            }
        }

        Instant now = clock.instant();
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .payloadSecret(encryptedSecret)
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

    private void validatePayloadKeys(Map<String, Object> payloadMap) {
        if (payloadMap == null || payloadMap.isEmpty()) {
            return;
        }
        for (String key : payloadMap.keySet()) {
            if (key != null) {
                String normalized = key.trim().toLowerCase().replace("_", "").replace("-", "");
                if (FORBIDDEN_PAYLOAD_KEYS.contains(normalized)) {
                    throw new IllegalArgumentException("Outbox payloadMap cannot contain sensitive key: " + key);
                }
            }
        }
    }
}
