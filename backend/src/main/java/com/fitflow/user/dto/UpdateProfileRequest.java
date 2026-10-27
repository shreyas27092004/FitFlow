package com.fitflow.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(max = 150, message = "Full name cannot exceed 150 characters")
        String fullName,

        @Size(max = 30, message = "Phone number cannot exceed 30 characters")
        String phone,

        LocalDate dateOfBirth,

        NotificationPrefsDto notificationPrefs,

        @NotNull(message = "Version is required for optimistic locking")
        Integer version
) {}
