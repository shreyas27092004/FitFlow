package com.fitflow.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Password is required to confirm account deletion")
        String password
) {}
