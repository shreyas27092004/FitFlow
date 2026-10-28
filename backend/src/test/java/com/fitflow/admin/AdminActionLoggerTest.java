package com.fitflow.admin;

import com.fitflow.admin.entity.AdminActionLog;
import com.fitflow.admin.repository.AdminActionLogRepository;
import com.fitflow.testutil.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminActionLoggerTest {

    private AdminActionLogRepository repository;
    private final MutableClock clock = MutableClock.ofUtc(Instant.parse("2026-10-28T00:00:00Z"));
    private AdminActionLogger logger;

    @BeforeEach
    void setUp() {
        repository = mock(AdminActionLogRepository.class);
        logger = new AdminActionLogger(repository, clock);
    }

    @Test
    @DisplayName("Safe payload writes to admin action log successfully")
    void testSuccessfulLog() {
        when(repository.save(any(AdminActionLog.class))).thenAnswer(i -> i.getArgument(0));

        String before = "{\"status\":\"PENDING\",\"quota\":5}";
        String after = "{\"status\":\"APPROVED\",\"quota\":10}";

        AdminActionLog result = logger.logAction(1L, "UPDATE_PLAN", "PLAN", 100L, before, after, "hash123");

        assertNotNull(result);
        assertEquals(1L, result.getAdminId());
        assertEquals("UPDATE_PLAN", result.getAction());
        assertEquals(clock.instant(), result.getCreatedAt());

        ArgumentCaptor<AdminActionLog> captor = ArgumentCaptor.forClass(AdminActionLog.class);
        verify(repository).save(captor.capture());
        assertEquals(before, captor.getValue().getBeforeJson());
        assertEquals(after, captor.getValue().getAfterJson());
    }

    @Test
    @DisplayName("Payload with 'password' key throws IllegalArgumentException and is rejected")
    void testRefusesToLogPasswords() {
        String sensitiveJson = "{\"email\":\"admin@fitflow.local\",\"password\":\"Secret1234!\"}";

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> logger.logAction(1L, "CHANGE_USER", "USER", 2L, null, sensitiveJson, "ip")
        );
        assertTrue(ex.getMessage().contains("sensitive keys"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Payload with 'token' or 'apiKey' keys throws IllegalArgumentException")
    void testRefusesToLogTokensAndApiKeys() {
        String tokenJson = "{\"token\":\"abc.xyz.123\"}";
        String apiKeyJson = "{\"apiKey\":\"AIzaSy1234\"}";

        assertThrows(
                IllegalArgumentException.class,
                () -> logger.logAction(1L, "ACTION", "TARGET", 1L, tokenJson, null, "ip")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> logger.logAction(1L, "ACTION", "TARGET", 1L, null, apiKeyJson, "ip")
        );
        verify(repository, never()).save(any());
    }
}
