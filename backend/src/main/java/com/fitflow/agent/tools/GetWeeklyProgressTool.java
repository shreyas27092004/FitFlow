package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetWeeklyProgressArgs;
import com.fitflow.wellness.WellnessFeedbackService;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetWeeklyProgressTool implements Tool {

    private final WellnessFeedbackService wellnessFeedbackService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_weekly_progress";
    }

    @Override
    public String description() {
        return "Get member weekly progress metrics including sessions attended, booked, streak count, and estimated calories burned.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "weekStart", Map.of("type", "string", "description", "Week start date (YYYY-MM-DD)")
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
        GetWeeklyProgressArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetWeeklyProgressArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            WeeklyProgressResponse progress = wellnessFeedbackService.getWeeklyProgress(context.userId(), args.weekStart());

            Map<String, Object> data = new HashMap<>();
            data.put("weekStart", args.weekStart() != null ? args.weekStart().toString() : "current");
            data.put("sessionsBooked", progress.bookedCount());
            data.put("sessionsAttended", progress.attendedCount());
            data.put("sessionsNoShow", progress.noShowCount());
            data.put("streakWeeks", progress.streakWeeks());
            data.put("estimatedKcalBurned", progress.estimatedKcalBurned());
            data.put("missedSessions", progress.missedSessions());

            String summary = String.format("Weekly progress for %s: %d attended of %d booked, streak %d weeks, estimated calories burned %s kcal.",
                    args.weekStart() != null ? args.weekStart().toString() : "current week",
                    progress.attendedCount(), progress.bookedCount(), progress.streakWeeks(),
                    progress.estimatedKcalBurned() != null ? progress.estimatedKcalBurned() : "N/A");
            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to get weekly progress: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
