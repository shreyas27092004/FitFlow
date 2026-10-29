package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassIntensity;

import java.time.Instant;

public record SearchSessionsArgs(
        @JsonProperty("from") Instant from,
        @JsonProperty("to") Instant to,
        @JsonProperty("category") ClassCategory category,
        @JsonProperty("intensity") ClassIntensity intensity,
        @JsonProperty("onlyAvailable") Boolean onlyAvailable
) {}
