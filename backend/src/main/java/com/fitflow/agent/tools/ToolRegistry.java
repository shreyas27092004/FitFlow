package com.fitflow.agent.tools;

import com.fitflow.agent.port.ToolDefinition;
import com.fitflow.agent.service.AgentAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final AgentAuditService auditService;

    public ToolRegistry(List<Tool> toolBeans, AgentAuditService auditService) {
        this.auditService = auditService;
        for (Tool tool : toolBeans) {
            tools.put(tool.name(), tool);
        }
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public Collection<Tool> getAllTools() {
        return tools.values();
    }

    public List<ToolDefinition> getDefinitionsForTools(Set<String> toolNames) {
        return toolNames.stream()
                .map(tools::get)
                .filter(java.util.Objects::nonNull)
                .map(t -> new ToolDefinition(t.name(), t.description(), t.parametersJsonSchema()))
                .toList();
    }

    public ToolResult execute(
            String agentName,
            Set<String> allowedToolsForAgent,
            String toolName,
            ToolContext context,
            Map<String, Object> rawArgs) {

        Tool tool = tools.get(toolName);

        // 1. Unknown tool
        if (tool == null) {
            auditService.recordAudit(
                    context.conversationId(), context.userId(), agentName, toolName,
                    rawArgs, "ERROR", "UNKNOWN_TOOL");
            return ToolResult.error("UNKNOWN_TOOL", "Unknown tool name: " + toolName);
        }

        // 2. Tool whitelist check for agent
        if (allowedToolsForAgent != null && !allowedToolsForAgent.contains(toolName)) {
            auditService.recordAudit(
                    context.conversationId(), context.userId(), agentName, toolName,
                    rawArgs, "FORBIDDEN_TOOL", "TOOL_NOT_ALLOWED_FOR_AGENT");
            return ToolResult.error("FORBIDDEN_TOOL", "Tool " + toolName + " is not permitted for agent " + agentName);
        }

        // 3. User roles check
        if (!tool.requiredRoles().isEmpty()) {
            boolean hasAnyRole = tool.requiredRoles().stream().anyMatch(context::hasRole);
            if (!hasAnyRole) {
                auditService.recordAudit(
                        context.conversationId(), context.userId(), agentName, toolName,
                        rawArgs, "FORBIDDEN_TOOL", "ROLE_INSUFFICIENT");
                return ToolResult.error("FORBIDDEN_TOOL", "User lacks required role for tool " + toolName);
            }
        }

        // 4. User permissions check
        if (!tool.requiredPermissions().isEmpty()) {
            boolean hasAnyPerm = tool.requiredPermissions().stream().anyMatch(context::hasPermission);
            if (!hasAnyPerm) {
                auditService.recordAudit(
                        context.conversationId(), context.userId(), agentName, toolName,
                        rawArgs, "FORBIDDEN_TOOL", "PERMISSION_INSUFFICIENT");
                return ToolResult.error("FORBIDDEN_TOOL", "User lacks required permission for tool " + toolName);
            }
        }

        // 5. Execute tool
        try {
            ToolResult result = tool.execute(context, rawArgs);
            String status = result.success()
                    ? (toolName.startsWith("propose_") ? "PENDING_CREATED" : "OK")
                    : "ERROR";

            auditService.recordAudit(
                    context.conversationId(), context.userId(), agentName, toolName,
                    rawArgs, status, result.errorCode());
            return result;
        } catch (Exception e) {
            log.error("Tool execution error for {}: {}", toolName, e.getMessage(), e);
            auditService.recordAudit(
                    context.conversationId(), context.userId(), agentName, toolName,
                    rawArgs, "ERROR", e.getMessage());
            return ToolResult.error("EXECUTION_ERROR", e.getMessage());
        }
    }
}
