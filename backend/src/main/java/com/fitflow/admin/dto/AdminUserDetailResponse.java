package com.fitflow.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminUserDetailResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        List<String> roles,
        List<String> permissions,
        String status,
        Boolean mustChangePassword,
        Integer failedLoginCount,
        Instant lockedUntil,
        Instant deletedAt,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {}
