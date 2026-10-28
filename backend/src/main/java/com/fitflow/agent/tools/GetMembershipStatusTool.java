package com.fitflow.agent.tools;

import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMembershipStatusTool implements Tool {

    private final MembershipService membershipService;

    @Override
    public String name() {
        return "get_membership_status";
    }

    @Override
    public String description() {
        return "Get the member's current active membership, plan code, remaining sessions, and validity.";
    }

    @Override
    public Map<String, Object> parametersJsonSchema() {
        return Map.of("type", "object", "properties", Map.of());
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
        MemberMembershipSummaryResponse summary = membershipService.getMemberMembership(context.userId());

        Map<String, Object> data = new HashMap<>();
        String currentPlanCode;
        String currentPlanName;
        int remainingSessions;
        String validUntil;

        if (summary.paid() != null) {
            currentPlanCode = summary.paid().planCode();
            currentPlanName = summary.paid().planName();
            remainingSessions = summary.paid().sessionsRemaining() != null ? summary.paid().sessionsRemaining() : 0;
            validUntil = summary.paid().endsAt() != null ? summary.paid().endsAt().toString() : "N/A";
            data.put("isPaid", true);
        } else {
            currentPlanCode = "FREE";
            currentPlanName = "Free Tier";
            remainingSessions = summary.freeSessionsRemaining();
            validUntil = "N/A";
            data.put("isPaid", false);
        }

        data.put("planCode", currentPlanCode);
        data.put("planName", currentPlanName);
        data.put("remainingSessions", remainingSessions);
        data.put("validUntil", validUntil);

        String textSummary = String.format("Current plan: %s (%s), Remaining sessions: %d, Valid until: %s",
                currentPlanName, currentPlanCode, remainingSessions, validUntil);

        return ToolResult.ok(data, textSummary);
    }
}
