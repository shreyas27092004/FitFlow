package com.fitflow.agent;

import com.fitflow.agent.core.AgentUsageService;
import com.fitflow.agent.entity.AgentUsage;
import com.fitflow.agent.repository.AgentUsageRepository;
import com.fitflow.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentUsageTest {

    private AgentUsageRepository usageRepository;
    private Clock fixedClock;
    private AgentUsageService usageService;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        usageRepository = mock(AgentUsageRepository.class);
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        usageService = new AgentUsageService(usageRepository, fixedClock);

        ReflectionTestUtils.setField(usageService, "dailyCallLimit", 200);
        ReflectionTestUtils.setField(usageService, "perMinuteLimit", 60);
    }

    @Test
    @DisplayName("Rate Limit: Exceeding 60 requests in one minute throws 429 RATE_LIMITED")
    void testPerMinuteRateLimitThrows429() {
        LocalDate today = LocalDate.now(fixedClock);
        when(usageRepository.findByUserIdAndDay(eq(42L), eq(today))).thenReturn(Optional.empty());

        // First 60 requests succeed
        for (int i = 0; i < 60; i++) {
            usageService.checkAndIncrementUsage(42L);
        }

        // 61st request in same minute throws RATE_LIMITED
        ApiException ex = assertThrows(ApiException.class, () -> usageService.checkAndIncrementUsage(42L));
        assertEquals(429, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("per minute"));
    }

    @Test
    @DisplayName("Daily Budget: Exceeding 200 daily requests throws 429 RATE_LIMITED")
    void testDailyLimitThrows429() {
        LocalDate today = LocalDate.now(fixedClock);
        AgentUsage currentUsage = new AgentUsage();
        currentUsage.setCalls(200); // at limit

        when(usageRepository.findByUserIdAndDay(eq(42L), eq(today))).thenReturn(Optional.of(currentUsage));

        ApiException ex = assertThrows(ApiException.class, () -> usageService.checkAndIncrementUsage(42L));
        assertEquals(429, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("Daily AI usage limit reached"));
    }

    @Test
    @DisplayName("Normal Usage: Increments usage in repository when under limits")
    void testNormalUsageIncrementsRepository() {
        LocalDate today = LocalDate.now(fixedClock);
        AgentUsage currentUsage = new AgentUsage();
        currentUsage.setCalls(5);

        when(usageRepository.findByUserIdAndDay(eq(42L), eq(today))).thenReturn(Optional.of(currentUsage));

        usageService.checkAndIncrementUsage(42L);

        verify(usageRepository, times(1)).incrementUsage(42L, today);
    }
}
