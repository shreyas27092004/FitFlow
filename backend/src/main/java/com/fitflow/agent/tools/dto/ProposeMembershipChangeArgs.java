package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProposeMembershipChangeArgs(
        @JsonProperty("targetPlanCode") String targetPlanCode,
        @JsonProperty("action") String action
) {}
