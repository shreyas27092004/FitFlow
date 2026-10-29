package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.dto.FitnessProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ProposeProfileSaveArgs(
        @NotNull(message = "profileDraft is required")
        @Valid
        @JsonProperty("profileDraft")
        FitnessProfile profileDraft
) {}
