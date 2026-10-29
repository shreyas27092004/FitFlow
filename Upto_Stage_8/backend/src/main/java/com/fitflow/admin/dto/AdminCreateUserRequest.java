package com.fitflow.admin.dto;

import com.fitflow.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminCreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name cannot exceed 150 characters")
        String fullName,

        @NotNull(message = "Role is required (TRAINER or ADMIN)")
        UserRole.Role role,

        @Size(max = 30, message = "Phone cannot exceed 30 characters")
        String phone,

        Long ratePerSessionPaise,

        List<String> permissions
) {}
