package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreviewMembershipChangeArgs(
        @JsonProperty("targetPlanCode") String targetPlanCode,
        @JsonProperty("action") String action
) {}
