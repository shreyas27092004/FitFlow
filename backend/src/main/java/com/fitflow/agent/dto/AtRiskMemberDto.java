package com.fitflow.agent.dto;

import java.util.List;

public record AtRiskMemberDto(
        Long memberId,
        String firstName,
        List<String> reasonCodes,
        Long daysToExpiry
) {}
