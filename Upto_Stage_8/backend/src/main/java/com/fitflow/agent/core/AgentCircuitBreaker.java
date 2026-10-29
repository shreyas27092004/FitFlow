package com.fitflow.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
public class AgentCircuitBreaker {

    public static final int FAILURE_THRESHOLD = 5;
    public static final long OPEN_DURATION_SECONDS = 60;
    public static final String FALLBACK_MESSAGE =
            "The AI assistant is temporarily unavailable. You can still use the app screens.";

    private final Clock clock;
    private int consecutiveFailures = 0;
    private Instant openUntil = Instant.MIN;

    public AgentCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isOpen() {
        Instant now = clock.instant();
        if (now.isBefore(openUntil)) {
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntil = Instant.MIN;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            openUntil = clock.instant().plusSeconds(OPEN_DURATION_SECONDS);
            log.warn("Agent circuit breaker OPEN until {} after {} consecutive failures", openUntil, consecutiveFailures);
        }
    }

    public synchronized void reset() {
        consecutiveFailures = 0;
        openUntil = Instant.MIN;
    }

    public synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
