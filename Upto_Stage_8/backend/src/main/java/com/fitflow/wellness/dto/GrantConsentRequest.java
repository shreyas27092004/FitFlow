package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.ConsentPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GrantConsentRequest(
        @NotBlank(message = "version is required")
        @JsonProperty("version")
        String version,

        @NotEmpty(message = "purposes must not be empty")
        @JsonProperty("purposes")
        List<ConsentPurpose> purposes
) {
    @Override
    public String toString() {
        return "GrantConsentRequest[REDACTED]";
    }
}
