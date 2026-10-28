package com.fitflow.agent.port;

import java.util.List;

public record ChatResponse(
        String content,
        List<ToolCall> toolCalls,
        String finishReason
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
