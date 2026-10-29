package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.service.AtRiskService;
import com.fitflow.agent.dto.AtRiskMemberDto;
import com.fitflow.agent.tools.dto.ListAtRiskMembersArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListAtRiskMembersTool implements Tool {

    private final AtRiskService atRiskService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "list_at_risk_members";
    }

    @Override
    public String description() {
        return "List members identified as at-risk of churning based on attendance drops or upcoming membership expiration.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "description", "Maximum number of members to return (max 25, default 25)")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("ADMIN");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("USER_READ");
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        ListAtRiskMembersArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ListAtRiskMembersArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        int limit = (args.limit() != null && args.limit() > 0) ? args.limit() : 25;

        try {
            List<AtRiskMemberDto> members = atRiskService.listAtRiskMembers(limit);

            Map<String, Object> data = new HashMap<>();
            data.put("members", members);
            data.put("count", members.size());

            String summary = String.format("Found %d at-risk member(s) eligible for retention actions.", members.size());
            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to list at risk members: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
