package com.fitflow.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AgentStatusResponse(
        @JsonProperty("mode")
        String mode,

        @JsonProperty("model")
        String model,

        @JsonProperty("configured")
        boolean configured,

        @JsonProperty("agents")
        List<AgentInfoDto> agents,

        @JsonProperty("dailyCallsRemaining")
        int dailyCallsRemaining
) {
    public record AgentInfoDto(
            @JsonProperty("id") String id,
            @JsonProperty("label") String label
    ) {}
}
