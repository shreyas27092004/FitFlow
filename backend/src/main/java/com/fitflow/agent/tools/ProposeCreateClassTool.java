package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeCreateClassArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeCreateClassTool implements Tool {

    private final DraftClassMetadataTool draftClassMetadataTool;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_create_class";
    }

    @Override
    public String description() {
        return "Propose creation of a new fitness class using drafted metadata for administrator review and confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("draft"),
                "properties", Map.of(
                        "draft", Map.of(
                                "type", "object",
                                "description", "Class metadata draft object"
                        )
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
        ProposeCreateClassArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeCreateClassArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.draft() == null || args.draft().isEmpty()) {
            return ToolResult.error("INVALID_ARGUMENTS", "draft is required");
        }

        List<String> validationErrors = draftClassMetadataTool.validateDraft(args.draft());
        if (!validationErrors.isEmpty()) {
            return ToolResult.error("INVALID_METADATA", String.join("; ", validationErrors));
        }

        try {
            String className = args.draft().get("name") != null ? args.draft().get("name").toString() : "New Class";
            String summary = String.format("Propose class '%s'. Draft only: please review metadata before members can see this class.", className);

            PendingActionProposal proposal = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    "propose_create_class",
                    args.draft(),
                    summary
            );

            Map<String, Object> data = Map.of(
                    "actionId", proposal.id(),
                    "className", className,
                    "summary", summary,
                    "expiresAt", proposal.expiresAt().toString(),
                    "status", "PROPOSED",
                    "requiresConfirmation", true
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to propose class creation: {}", e.getMessage(), e);
            return ToolResult.error("PROPOSAL_FAILED", e.getMessage());
        }
    }
}
