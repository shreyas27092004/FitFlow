package com.fitflow.admin.dto;

import com.fitflow.notification.entity.OutboxEvent;

import java.time.Instant;

public record OutboxEventSummaryResponse(
        Long id,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String status,
        Integer attempts,
        Instant nextAttemptAt,
        Instant sentAt,
        String lastError,
        Instant createdAt
) {
    public static OutboxEventSummaryResponse from(OutboxEvent event) {
        return new OutboxEventSummaryResponse(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getStatus().name(),
                event.getAttempts(),
                event.getNextAttemptAt(),
                event.getSentAt(),
                event.getLastError(),
                event.getCreatedAt()
        );
    }
}
