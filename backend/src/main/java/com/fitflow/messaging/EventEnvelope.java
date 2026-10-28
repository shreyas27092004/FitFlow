package com.fitflow.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        Long eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        Instant occurredAt,
        Map<String, Object> payload,
        String enc
) {
    public EventEnvelope {
        if (payload == null) {
            payload = Collections.emptyMap();
        }
    }
}
