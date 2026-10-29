package com.fitflow.agent.controller;

import com.fitflow.agent.core.AgentStatusService;
import com.fitflow.agent.dto.AgentStatusResponse;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent Status", description = "AI Agent runtime and configuration status")
public class AgentStatusController {

    private final AgentStatusService agentStatusService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current AI agent status (mode, model, available agents for role, and daily calls remaining)")
    public ResponseEntity<AgentStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.getId() : null;
        Set<String> roles = principal != null && principal.getRoles() != null ? new HashSet<>(principal.getRoles()) : Set.of();
        return ResponseEntity.ok(agentStatusService.getStatus(userId, roles));
    }
}
