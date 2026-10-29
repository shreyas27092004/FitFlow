package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.entity.IdempotencyRecord;
import com.fitflow.booking.repository.IdempotencyRecordRepository;
import com.fitflow.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IdempotencyServiceTest {

    private IdempotencyRecordRepository repository;
    private ObjectMapper objectMapper;
    private Clock clock;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(IdempotencyRecordRepository.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);
        service = new IdempotencyService(repository, objectMapper, clock);
    }

    @Test
    @DisplayName("validateKey() throws VALIDATION_FAILED when key is null or blank")
    void testValidateKeyNullOrBlank() {
        assertThrows(ApiException.class, () -> service.validateKey(null));
        assertThrows(ApiException.class, () -> service.validateKey("   "));
    }

    @Test
    @DisplayName("validateKey() throws VALIDATION_FAILED when key is too short (< 8 chars)")
    void testValidateKeyTooShort() {
        assertThrows(ApiException.class, () -> service.validateKey("short"));
    }

    @Test
    @DisplayName("validateKey() throws VALIDATION_FAILED when key is too long (> 64 chars)")
    void testValidateKeyTooLong() {
        String longKey = "a".repeat(65);
        assertThrows(ApiException.class, () -> service.validateKey(longKey));
    }

    @Test
    @DisplayName("validateKey() passes for valid 8-64 character keys")
    void testValidateKeyValid() {
        assertDoesNotThrow(() -> service.validateKey("valid-key-1234"));
        assertDoesNotThrow(() -> service.validateKey("a".repeat(64)));
    }

    @Test
    @DisplayName("computeHash() produces consistent SHA-256 hex string")
    void testComputeHashConsistent() {
        Map<String, Object> body = Map.of("sessionId", 42L);
        String hash1 = service.computeHash(body);
        String hash2 = service.computeHash(body);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("check() returns empty when key is not found")
    void testCheckNotFound() {
        when(repository.findByUserIdAndIdempotencyKey(10L, "key-new"))
                .thenReturn(Optional.empty());

        Optional<IdempotencyService.CachedIdempotentResponse> res = service.check(10L, "key-new", "hash123");
        assertFalse(res.isPresent());
    }

    @Test
    @DisplayName("check() returns cached response when requestHash matches")
    void testCheckMatchingHashReturnsCached() {
        IdempotencyRecord record = new IdempotencyRecord(10L, "key-hit", "hash123", 201, "{\"status\":\"CONFIRMED\"}", clock.instant());
        when(repository.findByUserIdAndIdempotencyKey(10L, "key-hit"))
                .thenReturn(Optional.of(record));

        Optional<IdempotencyService.CachedIdempotentResponse> res = service.check(10L, "key-hit", "hash123");
        assertTrue(res.isPresent());
        assertEquals(201, res.get().status());
        assertEquals("{\"status\":\"CONFIRMED\"}", res.get().payload());
    }

    @Test
    @DisplayName("check() throws IDEMPOTENCY_REPLAY_MISMATCH when requestHash differs")
    void testCheckMismatchThrows() {
        IdempotencyRecord record = new IdempotencyRecord(10L, "key-hit", "hash123", 201, "{\"status\":\"CONFIRMED\"}", clock.instant());
        when(repository.findByUserIdAndIdempotencyKey(10L, "key-hit"))
                .thenReturn(Optional.of(record));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.check(10L, "key-hit", "differentHash456")
        );
        assertEquals("IDEMPOTENCY_REPLAY_MISMATCH", ex.getErrorCode().name());
    }

    @Test
    @DisplayName("record() persists new IdempotencyRecord")
    void testRecordPersistsEntity() {
        service.record(10L, "key-rec", "hash123", 201, Map.of("bookingId", 1L));
        verify(repository).save(any(IdempotencyRecord.class));
    }
}
