package com.fitflow.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnMissingBean(value = SessionBookingHandler.class, ignored = DefaultSessionBookingHandler.class)
public class DefaultSessionBookingHandler implements SessionBookingHandler {

    @Override
    public String getMemberBookingStatus(Long sessionId, Long memberId) {
        return null;
    }

    @Override
    public Map<Long, String> getMemberBookingStatuses(List<Long> sessionIds, Long memberId) {
        return Collections.emptyMap();
    }

    @Override
    public int cancelConfirmedBookingsForSession(Long sessionId, String reason) {
        return 0;
    }

    @Override
    public List<AttendeeSummary> getAttendeesForSession(Long sessionId) {
        return Collections.emptyList();
    }
}
