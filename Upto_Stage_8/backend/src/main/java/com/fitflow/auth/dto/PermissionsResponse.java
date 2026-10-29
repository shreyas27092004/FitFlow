package com.fitflow.auth.dto;

import java.util.List;

public record PermissionsResponse(
        List<String> roles,
        List<String> permissions
) {}
