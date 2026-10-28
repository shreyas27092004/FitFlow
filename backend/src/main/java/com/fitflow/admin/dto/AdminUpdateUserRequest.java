package com.fitflow.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @Size(max = 150, message = "Full name cannot exceed 150 characters")
        String fullName,

        @Size(max = 30, message = "Phone cannot exceed 30 characters")
        String phone,

        @NotNull(message = "Version is required for optimistic locking")
        Integer version
) {}
