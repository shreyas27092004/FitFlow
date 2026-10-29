package com.fitflow.session.dto;

import java.util.List;

public record UtilizationReportResponse(
        List<ClassUtilizationDto> classUtilization,
        List<WeeklyUtilizationDto> weeklyUtilization
) {
    public record ClassUtilizationDto(
            Long classId,
            String className,
            long totalSessions,
            long totalCapacity,
            long totalBooked,
            double utilizationPercent
    ) {}

    public record WeeklyUtilizationDto(
            String weekLabel,
            long totalSessions,
            long totalCapacity,
            long totalBooked,
            double utilizationPercent
    ) {}
}
