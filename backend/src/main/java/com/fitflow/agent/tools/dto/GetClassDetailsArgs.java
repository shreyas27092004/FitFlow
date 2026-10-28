package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record GetClassDetailsArgs(
        @NotNull(message = "classId is required")
        @JsonProperty("classId")
        Long classId
) {}
