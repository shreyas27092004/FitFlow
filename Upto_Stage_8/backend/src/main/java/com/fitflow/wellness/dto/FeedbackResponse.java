package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeedbackResponse(
        @JsonProperty("proposalId")
        Long proposalId,

        @JsonProperty("message")
        String message
) {
    @Override
    public String toString() {
        return "FeedbackResponse[REDACTED]";
    }
}
