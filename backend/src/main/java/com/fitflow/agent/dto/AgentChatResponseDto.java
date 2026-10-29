package com.fitflow.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AgentChatResponseDto(
        @JsonProperty("conversationId")
        Long conversationId,

        @JsonProperty("reply")
        String reply,

        @JsonProperty("agentName")
        String agentName,

        @JsonProperty("circuitBreakerTripped")
        Boolean circuitBreakerTripped,

        @JsonProperty("toolCalls")
        List<ToolCallStatusDto> toolCalls,

        @JsonProperty("pendingActions")
        List<PendingActionProposalDto> pendingActions,

        @JsonProperty("guard")
        GuardStatusDto guard
) {
    public AgentChatResponseDto(Long conversationId, String reply, String agentName, Boolean circuitBreakerTripped) {
        this(conversationId, reply, agentName, circuitBreakerTripped, List.of(), List.of(), new GuardStatusDto(false));
    }

    public record ToolCallStatusDto(
            @JsonProperty("name") String name,
            @JsonProperty("status") String status
    ) {}

    public record PendingActionProposalDto(
            @JsonProperty("id") Long id,
            @JsonProperty("summary") String summary,
            @JsonProperty("expiresAt") String expiresAt
    ) {}

    public record GuardStatusDto(
            @JsonProperty("replacedByFallback") boolean replacedByFallback
    ) {}
}

