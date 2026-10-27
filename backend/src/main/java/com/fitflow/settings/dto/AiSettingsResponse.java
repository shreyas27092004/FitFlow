package com.fitflow.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

public record AiSettingsResponse(
        boolean configured,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String maskedLast4,
        String source,
        String model,
        String mode,
        Long updatedBy,
        Instant updatedAt
) {}
