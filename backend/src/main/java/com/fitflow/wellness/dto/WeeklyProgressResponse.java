package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WeeklyProgressResponse(
        @JsonProperty("bookedCount")
        int bookedCount,

        @JsonProperty("attendedCount")
        int attendedCount,

        @JsonProperty("noShowCount")
        int noShowCount,

        @JsonProperty("streakWeeks")
        int streakWeeks,

        @JsonProperty("estimatedKcalBurned")
        Integer estimatedKcalBurned,

        @JsonProperty("missedSessions")
        List<MissedSessionDto> missedSessions
) {
    @Override
    public String toString() {
        return "WeeklyProgressResponse[REDACTED]";
    }
}
