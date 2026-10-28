package com.fitflow.settings;

import com.fitflow.settings.dto.SecretResponseDto;
import com.fitflow.settings.dto.SetSecretRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SecretsServiceIT {

    @Autowired
    private SecretsService secretsService;

    @Test
    @DisplayName("SecretsService can store, retrieve, check existence, and delete encrypted app secrets")
    void testSetGetExistsDelete() {
        String secretName = "gemini_api_key_test";
        String secretValue = "AIzaSySecretTestKey9876";

        secretsService.set(secretName, secretValue, null);

        assertTrue(secretsService.exists(secretName));

        Optional<String> retrieved = secretsService.get(secretName);
        assertTrue(retrieved.isPresent());
        assertEquals(secretValue, retrieved.get());

        // Test masked
        Optional<String> masked = secretsService.masked(secretName);
        assertTrue(masked.isPresent());
        assertEquals("9876", masked.get());
        assertTrue(masked.get().length() <= 4, "Masked secret must not exceed 4 characters");

        // Delete
        secretsService.delete(secretName);
        assertFalse(secretsService.exists(secretName));
        assertTrue(secretsService.get(secretName).isEmpty());
    }

    @Test
    @DisplayName("masked() never returns more than 4 characters even for short or long secrets")
    void testMaskedLengthBoundaries() {
        secretsService.set("short_secret", "abc", null);
        assertEquals("abc", secretsService.masked("short_secret").orElse(""));

        secretsService.set("long_secret", "very_long_secret_token_value_ending_with_7890", null);
        String masked = secretsService.masked("long_secret").orElse("");
        assertEquals("7890", masked);
        assertTrue(masked.length() <= 4);

        secretsService.delete("short_secret");
        secretsService.delete("long_secret");
    }

    @Test
    @DisplayName("Secrets values NEVER appear in toString() of any DTO")
    void testSecretNeverAppearsInDtoToString() {
        String rawSecret = "SuperSensitivePasswordOrApiKey123!";

        SetSecretRequest setReq = new SetSecretRequest(rawSecret);
        String setReqStr = setReq.toString();
        assertFalse(setReqStr.contains(rawSecret), "SetSecretRequest.toString() must NOT contain the raw secret!");

        SecretResponseDto resp = new SecretResponseDto("gemini_key", "123!", 1, Instant.now());
        String respStr = resp.toString();
        assertFalse(respStr.contains(rawSecret), "SecretResponseDto.toString() must NOT contain the raw secret!");
        assertTrue(respStr.contains("123!"), "SecretResponseDto may only contain the masked last-4 characters");
    }
}
