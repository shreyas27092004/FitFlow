package com.fitflow.membership.dto;

public record ChangePreviewRequest(
        String targetPlanCode,
        String action
) {
}
