package com.fitflow.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EmailRateLimiter {

    private final int maxPerHour;
    private final Map<Long, Deque<Instant>> userTimestamps = new ConcurrentHashMap<>();

    public EmailRateLimiter(@Value("${fitflow.mail.max-per-recipient-per-hour:20}") int maxPerHour) {
        this.maxPerHour = maxPerHour;
    }

    public synchronized boolean isAllowed(Long userId, Instant now) {
        if (userId == null) {
            return true;
        }

        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        Deque<Instant> deque = userTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());

        while (!deque.isEmpty() && deque.peekFirst().isBefore(oneHourAgo)) {
            deque.pollFirst();
        }

        if (deque.size() >= maxPerHour) {
            log.warn("Rate limit exceeded for user id {}: reached {} emails per hour limit", userId, maxPerHour);
            return false;
        }

        deque.addLast(now);
        return true;
    }

    public synchronized void reset() {
        userTimestamps.clear();
    }
}
