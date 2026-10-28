package com.fitflow.agent.guard;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class NoSecretsGuard {

    private static final Pattern GEMINI_KEY_PATTERN = Pattern.compile("AIza[0-9A-Za-z-_]{35}");

    public boolean containsSecret(String text, String knownApiKey) {
        if (text == null) {
            return false;
        }

        if (knownApiKey != null && !knownApiKey.isBlank() && text.contains(knownApiKey.trim())) {
            return true;
        }

        return GEMINI_KEY_PATTERN.matcher(text).find();
    }
}
