package com.fitflow.agent.dto;

import java.util.List;

public record DemandInsightDto(
        List<DemandSuggestionDto> suggestions,
        List<SlotStatsDto> slots
) {
    public record DemandSuggestionDto(
            String type, // "ADD_SESSION" or "REDUCE_OR_MOVE"
            Long classId,
            String className,
            int dayOfWeek,
            int hour,
            String triggeringNumbers
    ) {}

    public record SlotStatsDto(
            Long classId,
            String className,
            int dayOfWeek,
            int hour,
            double averageFillRate,
            long totalWaitlist,
            double cancellationRate
    ) {}
}
