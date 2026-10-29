package com.fitflow.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetAiKeyRequest(
        @NotBlank(message = "API key is required")
        @Size(min = 20, max = 200, message = "API key must be between 20 and 200 characters")
        @Pattern(regexp = "^AIza[^\\s]*$", message = "API key must start with 'AIza' and contain no whitespace")
        String apiKey
) {}
