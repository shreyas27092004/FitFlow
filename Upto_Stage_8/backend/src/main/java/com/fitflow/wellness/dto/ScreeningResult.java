package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.ScreeningStatus;

import java.util.List;

public record ScreeningResult(
        @JsonProperty("status")
        ScreeningStatus status,

        @JsonProperty("reasons")
        List<String> reasons,

        @JsonProperty("doctorCheckRecommended")
        boolean doctorCheckRecommended
) {
    @Override
    public String toString() {
        return "ScreeningResult[REDACTED]";
    }
}
