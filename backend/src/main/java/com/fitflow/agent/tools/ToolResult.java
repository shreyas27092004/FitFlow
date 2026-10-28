package com.fitflow.agent.tools;

import java.util.Map;

public record ToolResult(
        Map<String, Object> data,
        String summary,
        boolean success,
        String errorCode
) {
    public static ToolResult ok(Map<String, Object> data, String summary) {
        return new ToolResult(data != null ? data : Map.of(), summary, true, null);
    }

    public static ToolResult error(String errorCode, String summary) {
        return new ToolResult(Map.of(), summary, false, errorCode);
    }
}
