package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ListAtRiskMembersArgs(
        @JsonProperty("limit") Integer limit
) {}
