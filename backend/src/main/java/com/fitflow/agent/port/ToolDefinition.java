package com.fitflow.agent.port;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersJsonSchema
) {}
