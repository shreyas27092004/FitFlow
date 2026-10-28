package com.fitflow.admin.dto;

import java.time.Instant;
import java.util.List;

public record UserSummaryDto(
        Long id,
        String email,
        String fullName,
        String phone,
        List<String> roles,
        String status,
        Instant createdAt
) {}
