package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.GetSessionPrepSummaryArgs;
import com.fitflow.wellness.TrainerPrepService;
import com.fitflow.wellness.dto.TrainerPrepResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetSessionPrepSummaryTool implements Tool {

    private final TrainerPrepService trainerPrepService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_session_prep_summary";
    }

    @Override
    public String description() {
        return "Get anonymous class preparation summary including injury counts and movement modifications for a teaching session.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", java.util.List.of("sessionId"),
                "properties", Map.of(
                        "sessionId", Map.of("type", "integer", "description", "Class Session ID")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("TRAINER");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of();
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        GetSessionPrepSummaryArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), GetSessionPrepSummaryArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.sessionId() == null) {
            return ToolResult.error("INVALID_ARGUMENTS", "sessionId is required");
        }

        try {
            TrainerPrepResponse prep = trainerPrepService.getTrainerPrepSummary(context.userId(), args.sessionId());

            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", args.sessionId());
            data.put("summaryAvailable", prep.summaryAvailable());
            data.put("attendeeCount", prep.attendeeCount());
            data.put("restrictionCounts", prep.restrictionCounts());
            data.put("suggestedModifications", prep.suggestedModifications());
            data.put("note", prep.note());

            String summary;
            if (prep.summaryAvailable()) {
                summary = String.format("Session %d prep: %d attendees. Restrictions: %s. %d modifications recommended.",
                        args.sessionId(), prep.attendeeCount(), prep.restrictionCounts(),
                        prep.suggestedModifications() != null ? prep.suggestedModifications().size() : 0);
            } else {
                summary = String.format("Session %d prep: Summary unavailable (%s). %d attendees.",
                        args.sessionId(), prep.note(), prep.attendeeCount());
            }

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to get session prep summary: {}", e.getMessage(), e);
            return ToolResult.error("EXECUTION_FAILED", e.getMessage());
        }
    }
}
