package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.service.AtRiskService;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeRetentionOfferArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeRetentionOfferTool implements Tool {

    private final AtRiskService atRiskService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_retention_offer";
    }

    @Override
    public String description() {
        return "Propose a retention offer (NUDGE_EMAIL or EXTRA_FREE_SESSION) for an at-risk member for administrator review and confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "required", java.util.List.of("memberId", "offerType"),
                "properties", Map.of(
                        "memberId", Map.of("type", "integer", "description", "Target member ID"),
                        "offerType", Map.of("type", "string", "enum", java.util.List.of("NUDGE_EMAIL", "EXTRA_FREE_SESSION"), "description", "Type of retention offer")
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
        return Set.of("USER_EDIT");
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> rawArgs) {
        ProposeRetentionOfferArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeRetentionOfferArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        if (args.targetMemberId() == null) {
            return ToolResult.error("INVALID_ARGUMENTS", "memberId is required");
        }
        if (args.offerType() == null || (!"NUDGE_EMAIL".equalsIgnoreCase(args.offerType()) && !"EXTRA_FREE_SESSION".equalsIgnoreCase(args.offerType()))) {
            return ToolResult.error("INVALID_ARGUMENTS", "offerType must be NUDGE_EMAIL or EXTRA_FREE_SESSION");
        }

        // Must be currently at-risk
        if (!atRiskService.isMemberAtRisk(args.targetMemberId())) {
            return ToolResult.error("NOT_AT_RISK", "Member " + args.targetMemberId() + " is not currently in the at-risk list");
        }

        try {
            String offerType = args.offerType().toUpperCase(java.util.Locale.ROOT);
            String summary = String.format("Propose retention offer '%s' for at-risk member %d", offerType, args.targetMemberId());

            Map<String, Object> actionArgs = Map.of(
                    "memberId", args.targetMemberId(),
                    "offerType", offerType
            );

            PendingActionProposal proposal = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    "propose_retention_offer",
                    actionArgs,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "actionId", proposal.id(),
                    "memberId", args.targetMemberId(),
                    "offerType", offerType,
                    "summary", summary,
                    "expiresAt", proposal.expiresAt().toString(),
                    "status", "PROPOSED",
                    "requiresConfirmation", true
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            log.error("Failed to propose retention offer: {}", e.getMessage(), e);
            return ToolResult.error("PROPOSAL_FAILED", e.getMessage());
        }
    }
}
