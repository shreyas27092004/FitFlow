package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetClassBurnArgs(
        @JsonProperty("classId") Long classId
) {}
