package com.fitflow.agent.tools;

import java.time.Clock;
import java.util.Set;

public record ToolContext(
        Long userId,
        Set<String> roles,
        Set<String> permissions,
        Long conversationId,
        Clock clock
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
