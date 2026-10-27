package com.fitflow.agent;

import com.fitflow.settings.AiConnectionTester;
import org.springframework.stereotype.Component;

/**
 * Stub per Rule R13: Real Google Gemini API client integration is added in Stage 8.
 */
@Component
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
