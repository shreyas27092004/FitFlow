package com.fitflow.notification;

import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxHealthIndicatorTest {

    private OutboxEventRepository outboxEventRepository;
    private Clock clock;
    private OutboxHealthIndicator healthIndicator;

    private final Instant baseTime = Instant.parse("2026-10-28T12:00:00Z");

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        healthIndicator = new OutboxHealthIndicator(outboxEventRepository, clock);
    }

    @Test
    @DisplayName("Health is UP and HEALTHY when no events pending or pending < 5 minutes")
    void testHealthyState() {
        when(outboxEventRepository.countByStatus(OutboxEvent.Status.PENDING)).thenReturn(2L);
        when(outboxEventRepository.countByStatus(OutboxEvent.Status.FAILED)).thenReturn(0L);

        OutboxEvent recentPending = OutboxEvent.builder()
                .id(1L)
                .status(OutboxEvent.Status.PENDING)
                .createdAt(baseTime.minusSeconds(60)) // 1 minute old
                .build();
        when(outboxEventRepository.findTopByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(Optional.of(recentPending));

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(2L, health.getDetails().get("pendingCount"));
        assertEquals(0L, health.getDetails().get("failedCount"));
        assertEquals(60L, health.getDetails().get("oldestPendingAgeSeconds"));
        assertEquals("HEALTHY", health.getDetails().get("status"));
    }

    @Test
    @DisplayName("Health remains UP with DEGRADED details when oldest pending event is > 5 minutes old")
    void testDegradedStateDoesNotFailOverallHealth() {
        when(outboxEventRepository.countByStatus(OutboxEvent.Status.PENDING)).thenReturn(5L);
        when(outboxEventRepository.countByStatus(OutboxEvent.Status.FAILED)).thenReturn(3L);

        OutboxEvent stalePending = OutboxEvent.builder()
                .id(2L)
                .status(OutboxEvent.Status.PENDING)
                .createdAt(baseTime.minus(Duration.ofMinutes(10))) // 10 minutes old (> 5m)
                .build();
        when(outboxEventRepository.findTopByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING))
                .thenReturn(Optional.of(stalePending));

        Health health = healthIndicator.health();

        // Overall status must still be UP (do not fail the whole health check)
        assertEquals(Status.UP, health.getStatus());
        assertEquals(5L, health.getDetails().get("pendingCount"));
        assertEquals(3L, health.getDetails().get("failedCount"));
        assertEquals(600L, health.getDetails().get("oldestPendingAgeSeconds"));
        assertEquals("DEGRADED", health.getDetails().get("status"));
    }
}
