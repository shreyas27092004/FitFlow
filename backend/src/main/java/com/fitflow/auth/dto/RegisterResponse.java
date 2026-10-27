package com.fitflow.auth.dto;

public record RegisterResponse(
        Long userId,
        String email,
        String fullName
) {}
