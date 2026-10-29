package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.wellness.model.SafetyVerdictType;

import java.util.List;

public record SafeSessionItem(
        @JsonProperty("session")
        SessionDetailResponse session,

        @JsonProperty("verdict")
        SafetyVerdictType verdict,

        @JsonProperty("reasonCodes")
        List<String> reasonCodes,

        @JsonProperty("humanReasons")
        List<String> humanReasons
) {
    @Override
    public String toString() {
        return "SafeSessionItem[REDACTED]";
    }
}
