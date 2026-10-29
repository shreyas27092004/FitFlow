package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.service.DemandService;
import com.fitflow.agent.dto.DemandInsightDto;
import com.fitflow.agent.tools.dto.GetDemandInsightsArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetDemandInsightsTool implements Tool {

    private final DemandService demandService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_demand_insights";
    }

    @Override
    public String description() {
        return "Get class fill rate, waitlist, and schedule demand analytics with ADD_SESSION and REDUCE_OR_MOVE suggestions.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "weeks", Map.of("type", "integer", "description", "Analysis lookback period in weeks (max 8, default 4)")
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
        GetDemandInsightsArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetDemandInsightsArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        int weeks = args.weeks() != null ? args.weeks() : 4;

        try {
            DemandInsightDto insights = demandService.getDemandInsights(weeks);

            Map<String, Object> data = new HashMap<>();
            data.put("suggestions", insights.suggestions());
            data.put("suggestionCount", insights.suggestions().size());
            data.put("slotsAnalyzed", insights.slots().size());
            data.put("slots", insights.slots());

            String summary = String.format("Demand analysis over %d weeks: generated %d schedule recommendation(s) across %d time slots.",
                    weeks, insights.suggestions().size(), insights.slots().size());

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to get demand insights: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
