package com.fitflow.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        List<String> roles,
        String status,
        Instant createdAt,
        NotificationPrefsDto notificationPrefs,
        Integer version
) {}
