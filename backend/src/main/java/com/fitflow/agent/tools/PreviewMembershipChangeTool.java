package com.fitflow.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.tools.dto.PreviewMembershipChangeArgs;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewMembershipChangeTool implements Tool {

    private final MembershipService membershipService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "preview_membership_change";
    }

    @Override
    public String description() {
        return "Preview the financial and session quota details for upgrading, downgrading, or cancelling a membership.";
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
        PreviewMembershipChangeArgs args;
        try {
            args = objectMapper.convertValue(rawArgs != null ? rawArgs : Map.of(), PreviewMembershipChangeArgs.class);
        } catch (Exception e) {
            return ToolResult.error("INVALID_ARGUMENTS", "Failed to parse arguments: " + e.getMessage());
        }

        try {
            ChangePreviewResponse preview = membershipService.changePreview(
                    context.userId(),
                    new ChangePreviewRequest(args.targetPlanCode(), args.action())
            );

            Map<String, Object> data = new HashMap<>();
            data.put("currentPlan", preview.currentPlan());
            data.put("targetPlan", preview.targetPlan());
            data.put("amountDuePaise", preview.amountDuePaise());
            data.put("creditPaise", preview.creditPaise());
            data.put("refundPaise", preview.refundPaise());
            data.put("explanation", preview.explanation());

            String summary;
            if ("CANCEL".equalsIgnoreCase(args.action())) {
                summary = String.format("Cancellation preview: Refund due Rs %d",
                        preview.refundPaise() != null ? preview.refundPaise() / 100 : 0);
            } else {
                summary = String.format("Change preview to %s: Credit Rs %d, Amount due now Rs %d",
                        preview.targetPlan() != null ? preview.targetPlan() : args.targetPlanCode(),
                        preview.creditPaise() != null ? preview.creditPaise() / 100 : 0,
                        preview.amountDuePaise() != null ? preview.amountDuePaise() / 100 : 0);
            }

            return ToolResult.ok(data, summary);
        } catch (Exception e) {
            return ToolResult.error("PREVIEW_ERROR", e.getMessage());
        }
    }
}
