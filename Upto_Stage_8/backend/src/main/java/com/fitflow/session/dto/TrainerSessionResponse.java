package com.fitflow.session.dto;

import com.fitflow.session.entity.ClassSession;

import java.time.Instant;
import java.util.List;

public record TrainerSessionResponse(
        Long id,
        Long classId,
        String className,
        Instant startsAt,
        Instant endsAt,
        String room,
        Integer capacity,
        Integer bookedCount,
        ClassSession.Status status,
        Long trainerRateSnapshotPaise,
        int confirmedAttendeeCount,
        List<TrainerAttendeeDto> attendees
) {
    public record TrainerAttendeeDto(
            Long bookingId,
            String firstName,
            String status
    ) {}
}
