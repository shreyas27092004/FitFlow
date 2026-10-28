package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MissedSessionDto(
        @JsonProperty("sessionId")
        Long sessionId,

        @JsonProperty("startsAt")
        Instant startsAt,

        @JsonProperty("className")
        String className
) {
    @Override
    public String toString() {
        return "MissedSessionDto[REDACTED]";
    }
}
