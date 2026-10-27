package com.fitflow.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeminiClientStubTest {

    private final GeminiClientStub stub = new GeminiClientStub();

    @Test
    @DisplayName("Stub Contract: testConnection returns false pending Stage 8 real client")
    void testConnectionReturnsFalse() {
        assertFalse(stub.testConnection("AIzaSyDummyKeyForTestingPurposes12345"));
    }

    @Test
    @DisplayName("Stub Contract: getTestMessage documents Stage 8 arrival")
    void testMessageDocumentsStage8() {
        assertEquals("Real Gemini client is added in Stage 8", stub.getTestMessage());
    }

    @Test
    @DisplayName("Stub Contract: testConnection safely accepts null key")
    void testConnectionAcceptsNull() {
        assertFalse(stub.testConnection(null));
    }
}
