package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeBookingArgs;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.SessionDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProposeBookingTool implements Tool {

    private final ClassSessionService classSessionService;
    private final MembershipService membershipService;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "propose_booking";
    }

    @Override
    public String description() {
        return "Propose booking a class session for the member. Creates a pending action requiring human confirmation.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "sessionId", Map.of("type", "integer", "description", "ID of the class session to book")
                ),
                "required", java.util.List.of("sessionId")
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
        ProposeBookingArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), ProposeBookingArgs.class);
            if (args.sessionId() == null) {
                return ToolResult.error("INVALID_ARGUMENTS", "sessionId is required");
            }
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            // Dry-run validation through services
            SessionDetailResponse session = classSessionService.getSessionById(args.sessionId(), context.userId());
            if (session.startsAt().isBefore(context.clock().instant())) {
                return ToolResult.error("SESSION_IN_PAST", "Cannot book a session that starts in the past");
            }

            MemberMembershipSummaryResponse membership = membershipService.getMemberMembership(context.userId());
            String planName = membership.paid() != null ? membership.paid().planName() : "Free Tier";
            int currentRemaining = membership.paid() != null
                    ? (membership.paid().sessionsRemaining() != null ? membership.paid().sessionsRemaining() : 0)
                    : membership.freeSessionsRemaining();
            int remainingAfter = Math.max(0, currentRemaining - 1);

            String summary = String.format("Book '%s' on %s with %s. This uses 1 session from your %s plan (%d left after).",
                    session.className(),
                    session.startsAt().toString(),
                    session.trainerName(),
                    planName,
                    remainingAfter);

            PendingActionProposal action = pendingActionService.propose(
                    context.conversationId(),
                    context.userId(),
                    name(),
                    args,
                    summary
            );

            Map<String, Object> data = Map.of(
                    "pendingActionId", action.id(),
                    "sessionId", args.sessionId(),
                    "summary", summary,
                    "status", "PENDING"
            );

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            return ToolResult.error("VALIDATION_ERROR", "Dry run failed: " + e.getMessage());
        }
    }
}
