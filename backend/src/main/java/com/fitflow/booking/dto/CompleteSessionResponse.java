package com.fitflow.booking.dto;

public record CompleteSessionResponse(
        Long sessionId,
        String status,
        Long earningsPaise
) {
}
