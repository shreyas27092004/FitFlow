package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.service.WeeklyPlanService;
import com.fitflow.agent.service.WeeklyPlanService.CandidatePlanResult;
import com.fitflow.agent.tools.dto.BuildWeeklyPlanCandidatesArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BuildWeeklyPlanCandidatesTool implements Tool {

    private final WeeklyPlanService weeklyPlanService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "build_weekly_plan_candidates";
    }

    @Override
    public String description() {
        return "Build deterministic candidate workout sessions for a given week adhering to recovery, variety, and goal preferences.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "weekStart", Map.of("type", "string", "description", "Week start date (YYYY-MM-DD), defaults to upcoming Monday")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("MEMBER");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        BuildWeeklyPlanCandidatesArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), BuildWeeklyPlanCandidatesArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            CandidatePlanResult result = weeklyPlanService.buildWeeklyPlanCandidates(context.userId(), args.weekStart());

            Map<String, Object> data = new HashMap<>();
            data.put("weekStart", result.weekStart().toString());
            data.put("targetCount", result.targetCount());
            data.put("entitlementRemaining", result.entitlementRemaining());
            data.put("candidates", result.candidates());
            data.put("ruleExplanations", result.ruleExplanations());

            String summary = String.format("Found %d candidate sessions for week starting %s. Target: %d.",
                    result.candidates().size(), result.weekStart(), result.targetCount());
            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to build weekly plan candidates: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
