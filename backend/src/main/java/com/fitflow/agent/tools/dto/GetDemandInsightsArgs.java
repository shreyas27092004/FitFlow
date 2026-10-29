package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetDemandInsightsArgs(
        @JsonProperty("weeks") Integer weeks
) {}
