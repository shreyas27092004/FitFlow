package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SuggestedModificationDto(
        @JsonProperty("code")
        String code,

        @JsonProperty("label")
        String label
) {
    @Override
    public String toString() {
        return "SuggestedModificationDto[REDACTED]";
    }
}
