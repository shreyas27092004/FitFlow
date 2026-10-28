package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.SafetyVerdictType;

import java.util.List;

public record SafetyVerdict(
        @JsonProperty("verdict")
        SafetyVerdictType verdict,

        @JsonProperty("reasonCodes")
        List<String> reasonCodes,

        @JsonProperty("humanReasons")
        List<String> humanReasons,

        @JsonProperty("disclaimer")
        String disclaimer
) {
    @Override
    public String toString() {
        return "SafetyVerdict[REDACTED]";
    }
}
