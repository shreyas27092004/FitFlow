package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.service.DemandService;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeAddSessionArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeAddSessionTool implements Tool {

    private final DemandService demandService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_add_session";
    }

    @Override
    public String description() {
        return "Propose adding a new class session for an identified high-demand schedule slot for administrator review and confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("classId", "trainerId", "startsAt", "room"),
                "properties", Map.of(
                        "classId", Map.of("type", "integer", "description", "Class ID"),
                        "trainerId", Map.of("type", "integer", "description", "Assigned Trainer ID"),
                        "startsAt", Map.of("type", "string", "description", "Session start ISO timestamp"),
                        "room", Map.of("type", "string", "description", "Room assignment")
                )
        );
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Set<String> requiredRoles() {
        return Set.of("ADMIN");
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("CLASS_EDIT");
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        ProposeAddSessionArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeAddSessionArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.classId() == null || args.assignedTrainerId() == null || args.startsAt() == null || args.room() == null || args.room().isBlank()) {
            return ToolResult.error("INVALID_ARGUMENTS", "classId, trainerId, startsAt, and room are all required");
        }

        ZonedDateTime zdt = args.startsAt().atZone(DemandService.ZONE);
        int dayOfWeek = zdt.getDayOfWeek().getValue();
        int hour = zdt.getHour();

        if (!demandService.hasAddSessionSuggestion(args.classId(), dayOfWeek, hour)) {
            return ToolResult.error("NO_DEMAND_SUGGESTION", String.format(
                    "No active ADD_SESSION recommendation matches classId %d on weekday %d at hour %02d:00",
                    args.classId(), dayOfWeek, hour));
        }

        try {
            String summary = String.format("Propose adding session for class %d with trainer %d at %s in room '%s'",
                    args.classId(), args.assignedTrainerId(), args.startsAt(), args.room());

            Map<String, Object> actionArgs = Map.of(
                    "classId", args.classId(),
                    "trainerId", args.assignedTrainerId(),
                    "startsAt", args.startsAt().toString(),
                    "room", args.room()
            );

            PendingActionProposal proposal = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    "propose_add_session",
                    actionArgs,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "actionId", proposal.id(),
                    "classId", args.classId(),
                    "trainerId", args.assignedTrainerId(),
                    "startsAt", args.startsAt().toString(),
                    "room", args.room(),
                    "summary", summary,
                    "expiresAt", proposal.expiresAt().toString(),
                    "status", "PROPOSED",
                    "requiresConfirmation", true
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to propose session addition: {}", e.getMessage(), e);
            return ToolResult.error("PROPOSAL_FAILED", e.getMessage());
        }
    }
}
