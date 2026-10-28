package com.fitflow.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailRateLimiterTest {

    private EmailRateLimiter rateLimiter;
    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        rateLimiter = new EmailRateLimiter(20);
    }

    @Test
    @DisplayName("Allows up to 20 emails per recipient per hour, 21st is dropped")
    void testMax20EmailsPerHour() {
        Long userId = 999L;

        // First 20 emails allowed
        for (int i = 0; i < 20; i++) {
            Instant emailTime = baseTime.plus(Duration.ofMinutes(i));
            assertTrue(rateLimiter.isAllowed(userId, emailTime), "Email " + (i + 1) + " should be allowed");
        }

        // 21st email within the hour must be dropped
        Instant droppedTime = baseTime.plus(Duration.ofMinutes(25));
        assertFalse(rateLimiter.isAllowed(userId, droppedTime), "21st email within same hour must be dropped");
    }

    @Test
    @DisplayName("Allows email after 1 hour sliding window has passed")
    void testSlidingWindowAfterOneHour() {
        Long userId = 999L;

        // Exhaust 20 emails at baseTime
        for (int i = 0; i < 20; i++) {
            rateLimiter.isAllowed(userId, baseTime);
        }

        // Immediately blocked
        assertFalse(rateLimiter.isAllowed(userId, baseTime.plus(Duration.ofMinutes(30))));

        // After 1 hour and 1 second -> allowed
        Instant afterWindow = baseTime.plus(Duration.ofHours(1)).plusSeconds(1);
        assertTrue(rateLimiter.isAllowed(userId, afterWindow), "Email after 1 hour should be allowed");
    }

    @Test
    @DisplayName("Reset clears the rate limiter state")
    void testReset() {
        Long userId = 999L;
        for (int i = 0; i < 20; i++) {
            rateLimiter.isAllowed(userId, baseTime);
        }
        assertFalse(rateLimiter.isAllowed(userId, baseTime));

        rateLimiter.reset();
        assertTrue(rateLimiter.isAllowed(userId, baseTime));
    }
}
