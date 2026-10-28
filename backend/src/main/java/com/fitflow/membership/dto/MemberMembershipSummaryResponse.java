package com.fitflow.membership.dto;

import java.time.Instant;

public record MemberMembershipSummaryResponse(
        int freeSessionsRemaining,
        PaidMembershipDto paid
) {
    public record PaidMembershipDto(
            String planCode,
            String planName,
            Instant startedAt,
            Instant endsAt,
            Integer sessionsRemaining,
            Integer daysRemaining
    ) {}
}
