package com.fitflow.membership.dto;

public record PlanDto(
        Long id,
        String code,
        String name,
        Long pricePaise,
        Integer durationDays,
        Integer sessionQuota,
        boolean isFree,
        Integer freeSessions,
        boolean active,
        int sortOrder
) {
}
