package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BodyMetricResponse(
        @JsonProperty("id")
        Long id,

        @JsonProperty("recordedOn")
        LocalDate recordedOn,

        @JsonProperty("weightKg")
        BigDecimal weightKg,

        @JsonProperty("heightCm")
        BigDecimal heightCm,

        @JsonProperty("createdAt")
        Instant createdAt
) {
    @Override
    public String toString() {
        return "BodyMetricResponse[REDACTED]";
    }
}
