package com.fitflow.auth.dto;

import java.util.List;

public record UserDto(
        Long id,
        String email,
        String fullName,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword
) {}
