package com.fitflow.session;

import java.util.List;
import java.util.Map;

public interface SessionBookingHandler {

    String getMemberBookingStatus(Long sessionId, Long memberId);

    Map<Long, String> getMemberBookingStatuses(List<Long> sessionIds, Long memberId);

    int cancelConfirmedBookingsForSession(Long sessionId, String reason);

    List<AttendeeSummary> getAttendeesForSession(Long sessionId);

    record AttendeeSummary(Long bookingId, String firstName, String status) {}
}
