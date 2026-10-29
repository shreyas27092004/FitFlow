package com.fitflow.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRedactionTest {

    @Test
    @DisplayName("Redacts sensitive keys matching (password|apiKey|authorization|token|secret) in JSON payloads")
    void testRedactJsonSensitiveKeys() {
        String json = "{\n" +
                "  \"email\": \"user@example.com\",\n" +
                "  \"password\": \"MySuperSecretPassword123!\",\n" +
                "  \"apiKey\": \"AIzaSyBxxxxSecret9999\",\n" +
                "  \"token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tokenpayload\",\n" +
                "  \"secret\": \"db_root_secret\",\n" +
                "  \"authorization\": \"Bearer eyJhbGciOiJIUzI1NiJ9\",\n" +
                "  \"className\": \"Vinyasa Yoga\"\n" +
                "}";

        String redacted = LogRedactor.redact(json);

        assertFalse(redacted.contains("MySuperSecretPassword123!"), "password value must be redacted");
        assertFalse(redacted.contains("AIzaSyBxxxxSecret9999"), "apiKey value must be redacted");
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tokenpayload"), "token value must be redacted");
        assertFalse(redacted.contains("db_root_secret"), "secret value must be redacted");
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"), "authorization value must be redacted");

        assertTrue(redacted.contains("\"password\":\"[REDACTED]\""));
        assertTrue(redacted.contains("\"apiKey\":\"[REDACTED]\""));
        assertTrue(redacted.contains("\"token\":\"[REDACTED]\""));
        assertTrue(redacted.contains("\"secret\":\"[REDACTED]\""));
        assertTrue(redacted.contains("\"authorization\":\"[REDACTED]\""));

        // Non-sensitive data remains intact
        assertTrue(redacted.contains("user@example.com"));
        assertTrue(redacted.contains("Vinyasa Yoga"));
    }

    @Test
    @DisplayName("Redacts sensitive key-value pairs in query strings and form parameters")
    void testRedactKeyValueParams() {
        String queryString = "username=admin&password=mySecretPass&apiKey=secretKey123&session=42";
        String redacted = LogRedactor.redact(queryString);

        assertFalse(redacted.contains("mySecretPass"));
        assertFalse(redacted.contains("secretKey123"));
        assertTrue(redacted.contains("password=[REDACTED]"));
        assertTrue(redacted.contains("apiKey=[REDACTED]"));
        assertTrue(redacted.contains("username=admin"));
        assertTrue(redacted.contains("session=42"));
    }

    @Test
    @DisplayName("Redacts sensitive HTTP headers")
    void testRedactHttpHeaders() {
        String header = "Authorization: Bearer super-secret-bearer-jwt-token";
        String redacted = LogRedactor.redact(header);

        assertFalse(redacted.contains("super-secret-bearer-jwt-token"));
        assertTrue(redacted.contains("Authorization: [REDACTED]"));
    }

    @Test
    @DisplayName("Case-insensitive matching for sensitive keys")
    void testCaseInsensitivity() {
        String mixedCaseJson = "{\"PASSWORD\": \"pass1\", \"ApiKey\": \"key2\", \"ToKeN\": \"tok3\"}";
        String redacted = LogRedactor.redact(mixedCaseJson);

        assertFalse(redacted.contains("pass1"));
        assertFalse(redacted.contains("key2"));
        assertFalse(redacted.contains("tok3"));
        assertTrue(redacted.contains("[REDACTED]"));
    }
}
