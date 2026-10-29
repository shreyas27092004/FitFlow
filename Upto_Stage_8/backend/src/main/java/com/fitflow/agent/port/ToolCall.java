package com.fitflow.agent.port;

public record ToolCall(
        String id,
        String name,
        String argumentsJson
) {}
