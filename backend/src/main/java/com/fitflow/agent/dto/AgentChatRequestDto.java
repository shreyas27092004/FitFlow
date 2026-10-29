package com.fitflow.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequestDto(
        Long conversationId,

        @NotBlank(message = "Message cannot be blank")
        @Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
        String message
) {}
