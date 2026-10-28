package com.fitflow.booking.dto;

public record MarkAttendanceResponse(
        Long sessionId,
        int markedCount
) {
}
