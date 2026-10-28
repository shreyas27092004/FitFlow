package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BodyMetricRequest(
        @NotNull(message = "recordedOn is required")
        @JsonProperty("recordedOn")
        LocalDate recordedOn,

        @NotNull(message = "weightKg is required")
        @DecimalMin(value = "30.0", message = "weightKg must be at least 30")
        @DecimalMax(value = "250.0", message = "weightKg must be at most 250")
        @JsonProperty("weightKg")
        BigDecimal weightKg,

        @DecimalMin(value = "120.0", message = "heightCm must be at least 120")
        @DecimalMax(value = "230.0", message = "heightCm must be at most 230")
        @JsonProperty("heightCm")
        BigDecimal heightCm
) {
    @Override
    public String toString() {
        return "BodyMetricRequest[REDACTED]";
    }
}
