package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeMembershipChangeArgs;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeMembershipChangeTool implements Tool {

    private final MembershipService membershipService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_membership_change";
    }

    @Override
    public String description() {
        return "Propose changing or cancelling the membership plan. Creates a pending action requiring human confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "targetPlanCode", Map.of("type", "string", "description", "Target plan code to switch to (e.g. PRO, BASIC)"),
                        "action", Map.of("type", "string", "description", "Action: CANCEL or leave empty for plan switch")
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
        ProposeMembershipChangeArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeMembershipChangeArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            // Dry run via changePreview
            ChangePreviewResponse preview = membershipService.changePreview(
                    context.userId(),
                    new ChangePreviewRequest(args.targetPlanCode(), args.action())
            );

            String summary;
            if ("CANCEL".equalsIgnoreCase(args.action())) {
                summary = String.format("Cancel %s membership. Refund due: Rs %d.",
                        preview.currentPlan() != null ? preview.currentPlan() : "current",
                        preview.refundPaise() != null ? preview.refundPaise() / 100 : 0);
            } else {
                summary = String.format("Change membership from %s to %s. Credit from current plan: Rs %d. Amount due now: Rs %d.",
                        preview.currentPlan() != null ? preview.currentPlan() : "FREE",
                        preview.targetPlan() != null ? preview.targetPlan() : args.targetPlanCode(),
                        preview.creditPaise() != null ? preview.creditPaise() / 100 : 0,
                        preview.amountDuePaise() != null ? preview.amountDuePaise() / 100 : 0);
            }

            PendingActionProposal action = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    name(),
                    args,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "pendingActionId", action.id(),
                    "targetPlanCode", args.targetPlanCode() != null ? args.targetPlanCode() : "",
                    "action", args.action() != null ? args.action() : "",
                    "summary", summary,
                    "status", "PENDING"
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            return ToolResult.error("VALIDATION_ERROR", "Dry run failed: " + e.getMessage());
        }
    }
}
