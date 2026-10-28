package com.fitflow.trainer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TrainerSummaryResponse(
        int upcomingSessionsCount,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Long earnings
) {}
