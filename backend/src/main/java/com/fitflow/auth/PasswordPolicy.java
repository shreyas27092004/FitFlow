package com.fitflow.auth;

import com.fitflow.common.error.ApiException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordPolicy {

    private static final Pattern LETTER_PATTERN = Pattern.compile("[a-zA-Z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");

    public void validate(String password, String email) {
        if (password == null || password.length() < 10) {
            throw ApiException.validationFailed("Password must be at least 10 characters long");
        }
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase();
            String normalizedPassword = password.trim().toLowerCase();
            if (normalizedPassword.equals(normalizedEmail)) {
                throw ApiException.validationFailed("Password cannot be identical to email");
            }
        }
        if (!LETTER_PATTERN.matcher(password).find()) {
            throw ApiException.validationFailed("Password must contain at least one letter");
        }
        if (!DIGIT_PATTERN.matcher(password).find()) {
            throw ApiException.validationFailed("Password must contain at least one digit");
        }
    }
}
