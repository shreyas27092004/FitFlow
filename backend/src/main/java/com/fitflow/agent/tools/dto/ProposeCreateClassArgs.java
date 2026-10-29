package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ProposeCreateClassArgs(
        @JsonProperty("draft") Map<String, Object> draft
) {}
