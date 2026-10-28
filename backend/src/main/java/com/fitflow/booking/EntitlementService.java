package com.fitflow.booking;

public interface EntitlementService {

    Entitlement reserve(Long memberId, Long sessionId, String idempotencyKey);

    void restore(Long bookingId, String reason);
}
