package com.fitflow.agent;

import com.fitflow.settings.AiConnectionTester;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub used in test profile to guarantee no network calls occur during automated test suite runs.
 */
@Component
@Profile("test")
public class GeminiClientStub implements AiConnectionTester {

    @Override
    public boolean testConnection(String apiKey) {
        return false;
    }

    @Override
    public String getTestMessage() {
        return "Real Gemini client is added in Stage 8";
    }
}
