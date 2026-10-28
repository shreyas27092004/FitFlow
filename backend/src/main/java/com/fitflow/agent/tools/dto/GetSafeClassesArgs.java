package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GetSafeClassesArgs(
        @JsonProperty("from") Instant from,
        @JsonProperty("to") Instant to
) {}
