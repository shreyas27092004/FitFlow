package com.fitflow.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetAiModelRequest(
        @NotBlank(message = "Model name is required")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,60}$", message = "Model name must match pattern ^[a-zA-Z0-9._-]{3,60}$")
        String model
) {}
