package com.fitflow.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogRedactor {

    private static final String SENSITIVE_KEYS = "password|apiKey|authorization|token|secret";
    private static final String MASK = "[REDACTED]";

    // Pattern for JSON: "password": "...", "apiKey": 1234, etc.
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "\"(?i)(" + SENSITIVE_KEYS + ")\"\\s*:\\s*(\"[^\"]*\"|[^,\\}\\]\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for key-value / query strings: password=secret&apiKey=123
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(" + SENSITIVE_KEYS + ")=([^&\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for HTTP header format: Authorization: Bearer ...
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "(?i)(" + SENSITIVE_KEYS + ")\\s*:\\s*([^\\r\\n]+)",
            Pattern.CASE_INSENSITIVE
    );

    private LogRedactor() {
    }

    public static String redact(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = JSON_PATTERN.matcher(message).replaceAll(mr -> {
            String key = mr.group(1);
            return "\"" + key + "\":\"" + MASK + "\"";
        });

        result = KEY_VALUE_PATTERN.matcher(result).replaceAll(mr -> {
            String key = mr.group(1);
            return key + "=" + MASK;
        });

        result = HEADER_PATTERN.matcher(result).replaceAll(mr -> {
            String key = mr.group(1);
            return key + ": " + MASK;
        });

        return result;
    }
}
