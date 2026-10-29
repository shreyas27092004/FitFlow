package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassBurnResponse(
        @JsonProperty("kcal")
        int kcal,

        @JsonProperty("assumptions")
        String assumptions,

        @JsonProperty("disclaimer")
        String disclaimer
) {
    @Override
    public String toString() {
        return "ClassBurnResponse[REDACTED]";
    }
}
