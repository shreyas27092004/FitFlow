package com.fitflow.agent.controller;

import com.fitflow.agent.core.AgentStatusService;
import com.fitflow.agent.dto.AgentStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent Status", description = "AI Agent runtime and configuration status")
public class AgentStatusController {

    private final AgentStatusService agentStatusService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current AI agent status (mock vs live mode, model name, and configuration status)")
    public ResponseEntity<AgentStatusResponse> getStatus() {
        return ResponseEntity.ok(agentStatusService.getStatus());
    }
}
