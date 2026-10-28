package com.fitflow.agent;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockLlmClient implements LlmClient {

    public static final String CANNED_RESPONSE = "FitFlow AI Coach mock deterministic response for: ";

    @Override
    public String generateResponse(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "FitFlow AI Assistant ready.";
        }
        return CANNED_RESPONSE + prompt.trim();
    }
}
