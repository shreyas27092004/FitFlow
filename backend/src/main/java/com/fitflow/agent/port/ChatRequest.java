package com.fitflow.agent.port;

import java.util.List;

public record ChatRequest(
        List<ChatMessage> messages,
        List<ToolDefinition> tools,
        Double temperature,
        Integer maxTokens
) {
    public ChatRequest {
        if (messages == null) {
            messages = List.of();
        }
        if (tools == null) {
            tools = List.of();
        }
    }
}
