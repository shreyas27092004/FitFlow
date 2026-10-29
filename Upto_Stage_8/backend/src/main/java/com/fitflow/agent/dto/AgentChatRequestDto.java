package com.fitflow.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequestDto(
        Long conversationId,
        @NotBlank(message = "Message cannot be blank")
        String message
) {}
