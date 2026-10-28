package com.fitflow.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record SetSecretRequest(
        @NotBlank(message = "Secret value must not be blank")
        String value
) {
    @Override
    public String toString() {
        return "SetSecretRequest[value=[PROTECTED]]";
    }
}
