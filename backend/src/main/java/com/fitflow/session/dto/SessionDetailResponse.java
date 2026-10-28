package com.fitflow.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitflow.session.entity.ClassSession;

import java.time.Instant;

public record SessionDetailResponse(
        Long id,
        Long classId,
        String className,
        String category,
        Integer durationMinutes,
        String intensity,
        String impactLevel,
        Long trainerId,
        String trainerName,
        Instant startsAt,
        Instant endsAt,
        String room,
        Integer capacity,
        Integer bookedCount,
        Integer seatsLeft,
        ClassSession.Status status,
        String seriesId,
        Long trainerRateSnapshotPaise,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String myBookingStatus,
        Integer version,
        Instant createdAt
) {
}
