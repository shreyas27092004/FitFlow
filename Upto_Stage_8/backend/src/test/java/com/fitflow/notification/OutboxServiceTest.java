package com.fitflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    private OutboxEventRepository outboxEventRepository;
    private EncryptionService encryptionService;
    private ObjectMapper objectMapper;
    private Clock clock;
    private OutboxService outboxService;

    private final Instant fixedTime = Instant.parse("2026-10-28T12:00:00Z");

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        encryptionService = mock(EncryptionService.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(fixedTime, ZoneOffset.UTC);

        outboxService = new OutboxService(outboxEventRepository, encryptionService, objectMapper, clock);

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "token", "tempPassword", "secret", "apiKey", "TOKEN", "Temp_Password", "api_key"})
    @DisplayName("OutboxService.append throws IllegalArgumentException if payloadMap has sensitive key")
    void testForbiddenKeysInPayloadThrowException(String forbiddenKey) {
        Map<String, Object> payload = Map.of(forbiddenKey, "sensitive-value");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                outboxService.append("USER", 1L, "user.test", payload)
        );
        assertTrue(ex.getMessage().contains("sensitive key"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "token", "tempPassword", "secret", "apiKey"})
    @DisplayName("OutboxService.appendWithSecret throws IllegalArgumentException if payloadMap has sensitive key")
    void testForbiddenKeysInPayloadThrowExceptionForAppendWithSecret(String forbiddenKey) {
        Map<String, Object> payload = Map.of(forbiddenKey, "sensitive-value");
        Map<String, Object> secret = Map.of("safeSecretKey", "value");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                outboxService.appendWithSecret("USER", 1L, "user.test", payload, secret)
        );
        assertTrue(ex.getMessage().contains("sensitive key"));
    }

    @Test
    @DisplayName("OutboxService.append successfully appends event with no secrets")
    void testAppendSuccessful() {
        Map<String, Object> payload = Map.of("userId", 42L, "action", "LOGIN");

        OutboxEvent event = outboxService.append("USER", 42L, "user.login", payload);

        assertNotNull(event);
        assertEquals("USER", event.getAggregateType());
        assertEquals(42L, event.getAggregateId());
        assertEquals("user.login", event.getEventType());
        assertEquals(OutboxEvent.Status.PENDING, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertEquals(fixedTime, event.getCreatedAt());
        assertEquals(fixedTime, event.getNextAttemptAt());
        assertNull(event.getPayloadSecret());
        assertTrue(event.getPayload().contains("\"userId\":42"));

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("OutboxService.appendWithSecret serializes and encrypts secretMap into payloadSecret")
    void testAppendWithSecretSuccessful() {
        Map<String, Object> payload = Map.of("userId", 10L, "role", "MEMBER");
        Map<String, Object> secret = Map.of("tempPassword", "superSecret123!");

        when(encryptionService.encryptString(any())).thenReturn("encrypted_cipher_text");

        OutboxEvent event = outboxService.appendWithSecret("USER", 10L, "user.created", payload, secret);

        assertNotNull(event);
        assertEquals("encrypted_cipher_text", event.getPayloadSecret());
        assertTrue(event.getPayload().contains("\"userId\":10"));
        assertTrue(!event.getPayload().contains("superSecret123!"));

        ArgumentCaptor<String> secretCaptor = ArgumentCaptor.forClass(String.class);
        verify(encryptionService).encryptString(secretCaptor.capture());
        assertTrue(secretCaptor.getValue().contains("\"tempPassword\":\"superSecret123!\""));
    }
}
