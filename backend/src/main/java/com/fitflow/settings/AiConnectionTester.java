package com.fitflow.settings;

public interface AiConnectionTester {
    boolean testConnection(String apiKey);
    String getTestMessage();
}
