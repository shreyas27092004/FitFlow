package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetSessionPrepSummaryArgs(
        @JsonProperty("sessionId") Long sessionId
) {}
