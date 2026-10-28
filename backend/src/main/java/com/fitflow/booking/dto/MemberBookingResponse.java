package com.fitflow.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberBookingResponse(
        Long bookingId,
        Long sessionId,
        String className,
        String category,
        Instant startsAt,
        Instant endsAt,
        String room,
        String trainerName,
        String status,
        Instant bookedAt,
        Instant cancelledAt
) {
}
