package com.fitflow.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminPermissionsRequest(
        @NotNull(message = "Permissions list is required")
        List<String> permissions
) {}
