package com.fitflow.auth;

import com.fitflow.common.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("Valid password with >= 10 chars, letter, and digit passes")
    void testValidPassword() {
        assertDoesNotThrow(() -> policy.validate("SecurePass123!", "user@example.com"));
    }

    @Test
    @DisplayName("Password with < 10 characters throws VALIDATION_FAILED")
    void testTooShortPassword() {
        ApiException ex = assertThrows(ApiException.class, () -> policy.validate("Short1!", "user@example.com"));
        assertTrue(ex.getMessage().contains("at least 10 characters"));
    }

    @Test
    @DisplayName("Password with no digits throws VALIDATION_FAILED")
    void testPasswordWithNoDigits() {
        ApiException ex = assertThrows(ApiException.class, () -> policy.validate("AllLettersPassword!", "user@example.com"));
        assertTrue(ex.getMessage().contains("at least one digit"));
    }

    @Test
    @DisplayName("Password with no letters throws VALIDATION_FAILED")
    void testPasswordWithNoLetters() {
        ApiException ex = assertThrows(ApiException.class, () -> policy.validate("123456789012!", "user@example.com"));
        assertTrue(ex.getMessage().contains("at least one letter"));
    }

    @Test
    @DisplayName("Password identical to email throws VALIDATION_FAILED")
    void testPasswordIdenticalToEmail() {
        ApiException ex = assertThrows(ApiException.class, () -> policy.validate("user123456@fitflow.com", "user123456@fitflow.com"));
        assertTrue(ex.getMessage().contains("cannot be identical to email"));
    }
}
