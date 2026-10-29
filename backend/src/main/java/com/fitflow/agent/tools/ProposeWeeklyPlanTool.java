package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.service.WeeklyPlanService;
import com.fitflow.agent.tools.dto.ProposeWeeklyPlanArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeWeeklyPlanTool implements Tool {

    private final WeeklyPlanService weeklyPlanService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_weekly_plan";
    }

    @Override
    public String description() {
        return "Propose a weekly fitness plan of selected candidate sessions for the member to review and confirm.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", java.util.List.of("sessionIds"),
                "properties", Map.of(
                        "weekStart", Map.of("type", "string", "description", "Week start date (YYYY-MM-DD)"),
                        "sessionIds", Map.of("type", "array", "items", Map.of("type", "integer"), "description", "List of candidate session IDs to schedule")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return false;
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
        ProposeWeeklyPlanArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeWeeklyPlanArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.sessionIds() == null || args.sessionIds().isEmpty()) {
            return ToolResult.error("INVALID_ARGUMENTS", "sessionIds cannot be empty");
        }

        LocalDate weekStart = args.weekStart() != null ? args.weekStart() :
                LocalDate.now(context.clock()).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));

        try {
            Long planId = weeklyPlanService.proposeWeeklyPlan(context.userId(), weekStart, args.sessionIds(), context.conversationId());

            String summary = String.format("Propose weekly workout plan for week of %s with %d sessions: %s",
                    weekStart, args.sessionIds().size(), args.sessionIds());

            Map<String, Object> actionArgs = Map.of(
                    "planId", planId,
                    "weekStart", weekStart.toString(),
                    "sessionIds", args.sessionIds()
            );

            PendingActionProposal proposal = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    "propose_weekly_plan",
                    actionArgs,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "planId", planId,
                    "actionId", proposal.id(),
                    "summary", summary,
                    "expiresAt", proposal.expiresAt().toString(),
                    "status", "PROPOSED",
                    "requiresConfirmation", true
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to propose weekly plan: {}", e.getMessage(), e);
            return ToolResult.error("PROPOSAL_FAILED", e.getMessage());
        }
    }
}
