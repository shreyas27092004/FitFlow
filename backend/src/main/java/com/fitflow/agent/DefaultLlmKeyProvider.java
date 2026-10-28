package com.fitflow.agent;

import com.fitflow.settings.SecretsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DefaultLlmKeyProvider implements LlmKeyProvider {

    private static final String GEMINI_SECRET_NAME = "gemini_api_key";

    private final SecretsService secretsService;
    private final String envGeminiApiKey;

    public DefaultLlmKeyProvider(
            SecretsService secretsService,
            @Value("${GEMINI_API_KEY:}") String envGeminiApiKey) {
        this.secretsService = secretsService;
        this.envGeminiApiKey = envGeminiApiKey;
    }

    @Override
    public Optional<String> currentKey() {
        // 1. Check dynamic database secrets first
        Optional<String> dbKey = secretsService.get(GEMINI_SECRET_NAME);
        if (dbKey.isPresent() && !dbKey.get().isBlank()) {
            return dbKey;
        }

        // 2. Check environment variable
        if (envGeminiApiKey != null && !envGeminiApiKey.isBlank()) {
            return Optional.of(envGeminiApiKey.trim());
        }

        // 3. Fallback to empty
        return Optional.empty();
    }
}
