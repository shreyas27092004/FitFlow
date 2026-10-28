package com.fitflow.agent.tools;

import java.util.Map;
import java.util.Set;

public interface Tool {
    String name();
    String description();
    Map<String, Object> parametersJsonSchema();
    boolean isReadOnly();
    Set<String> requiredRoles();
    Set<String> requiredPermissions();
    ToolResult execute(ToolContext context, Map<String, Object> rawArgs);
}
