package com.fitflow.agent.controller;

import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.dto.AgentChatRequestDto;
import com.fitflow.agent.dto.AgentChatResponseDto;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent Chat", description = "Multi-agent conversational assistant for FitFlow members")
public class AgentChatController {

    private final AgentRuntime agentRuntime;
    private final PendingActionService pendingActionService;

    @PostMapping("/chat")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Send message to member specialist agents")
    public ResponseEntity<AgentChatResponseDto> chat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AgentChatRequestDto request) {

        Set<String> roles = principal.getRoles() != null ? new HashSet<>(principal.getRoles()) : Set.of("MEMBER");
        Set<String> perms = principal.getPermissions() != null ? new HashSet<>(principal.getPermissions()) : Set.of();

        AgentRuntime.AgentResponse response = agentRuntime.run(new AgentRuntime.AgentRequest(
                principal.getId(),
                roles,
                perms,
                request.conversationId(),
                request.message()
        ));

        return ResponseEntity.ok(new AgentChatResponseDto(
                response.conversationId(),
                response.reply(),
                response.agentName(),
                response.circuitBreakerTripped()
        ));
    }

    @PostMapping("/actions/{id}/confirm")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Confirm and execute a proposed pending action")
    public ResponseEntity<Map<String, Object>> confirmAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id) {

        Map<String, Object> result = pendingActionService.confirm(id, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/actions/{id}/reject")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Reject a proposed pending action")
    public ResponseEntity<Map<String, Object>> rejectAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id) {

        pendingActionService.reject(id, principal.getId());
        return ResponseEntity.ok(Map.of("status", "REJECTED"));
    }
}
