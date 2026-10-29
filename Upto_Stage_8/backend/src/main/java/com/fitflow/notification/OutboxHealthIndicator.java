package com.fitflow.notification;

import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component("outboxHealthIndicator")
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    @Override
    public Health health() {
        long pendingCount = outboxEventRepository.countByStatus(OutboxEvent.Status.PENDING);
        long failedCount = outboxEventRepository.countByStatus(OutboxEvent.Status.FAILED);

        Optional<OutboxEvent> oldestPending = outboxEventRepository.findTopByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);
        long oldestPendingAgeSeconds = 0;
        boolean degraded = false;

        if (oldestPending.isPresent()) {
            Instant created = oldestPending.get().getCreatedAt();
            oldestPendingAgeSeconds = Math.max(0, Duration.between(created, clock.instant()).getSeconds());
            if (oldestPendingAgeSeconds > 300) { // 5 minutes
                degraded = true;
            }
        }

        Health.Builder builder = Health.up()
                .withDetail("pendingCount", pendingCount)
                .withDetail("failedCount", failedCount)
                .withDetail("oldestPendingAgeSeconds", oldestPendingAgeSeconds)
                .withDetail("status", degraded ? "DEGRADED" : "HEALTHY");

        return builder.build();
    }
}
