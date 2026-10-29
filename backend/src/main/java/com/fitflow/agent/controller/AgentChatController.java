package com.fitflow.agent.controller;

import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.dto.AgentChatRequestDto;
import com.fitflow.agent.dto.AgentChatResponseDto;
import com.fitflow.agent.dto.AgentChatResponseDto.GuardStatusDto;
import com.fitflow.agent.dto.AgentChatResponseDto.PendingActionProposalDto;
import com.fitflow.agent.dto.AgentChatResponseDto.ToolCallStatusDto;
import com.fitflow.agent.dto.AgentConversationDto;
import com.fitflow.agent.dto.AgentMessageDto;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.memory.AgentMemoryService;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.common.error.ApiException;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Agent Chat", description = "Multi-agent conversational assistant for FitFlow members, trainers, and administrators")
public class AgentChatController {

    public static volatile boolean sanityBreakStreamRawTokensBeforeGuard = false;

    private final AgentRuntime agentRuntime;
    private final PendingActionService pendingActionService;
    private final AgentMemoryService agentMemoryService;
    private final Clock clock;

    // Track active SSE streams per user (limit: 1 active stream per user)
    private final Map<Long, SseEmitter> activeStreams = new ConcurrentHashMap<>();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @PostMapping("/chat")
    @Operation(summary = "Send message to specialist agent")
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

        List<ToolCallStatusDto> toolCalls = response.toolCalls() != null ? response.toolCalls().stream()
                .map(t -> new ToolCallStatusDto(t.name(), t.status())).toList() : List.of();

        List<PendingActionProposalDto> pendingActions = response.pendingActions() != null ? response.pendingActions().stream()
                .map(p -> new PendingActionProposalDto(p.id(), p.summary(), p.expiresAt())).toList() : List.of();

        GuardStatusDto guard = response.guard() != null
                ? new GuardStatusDto(response.guard().replacedByFallback())
                : new GuardStatusDto(false);

        return ResponseEntity.ok(new AgentChatResponseDto(
                response.conversationId(),
                response.reply(),
                response.agentName(),
                response.circuitBreakerTripped(),
                toolCalls,
                pendingActions,
                guard
        ));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-Sent Events streaming chat endpoint (GET)")
    public SseEmitter chatStreamGet(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "conversationId", required = false) Long conversationId,
            @RequestParam(name = "message") String message) {
        return handleStream(principal, new AgentChatRequestDto(conversationId, message));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-Sent Events streaming chat endpoint (POST)")
    public SseEmitter chatStreamPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AgentChatRequestDto request) {
        return handleStream(principal, request);
    }

    private SseEmitter handleStream(UserPrincipal principal, AgentChatRequestDto request) {
        Long userId = principal.getId();

        // 1. One active stream per user check
        SseEmitter emitter = new SseEmitter(60_000L); // 60s timeout
        SseEmitter existing = activeStreams.putIfAbsent(userId, emitter);
        if (existing != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("code", "ALREADY_STREAMING")));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        emitter.onCompletion(() -> activeStreams.remove(userId));
        emitter.onTimeout(() -> activeStreams.remove(userId));
        emitter.onError(ex -> activeStreams.remove(userId));

        // 2. Validate input
        if (request.message() == null || request.message().trim().isBlank()) {
            sendErrorAndComplete(emitter, "VALIDATION_FAILED", "Message cannot be blank", userId);
            return emitter;
        }

        // 3. Execute turn asynchronously
        sseExecutor.submit(() -> {
            try {
                Set<String> roles = principal.getRoles() != null ? new HashSet<>(principal.getRoles()) : Set.of("MEMBER");
                Set<String> perms = principal.getPermissions() != null ? new HashSet<>(principal.getPermissions()) : Set.of();

                if (sanityBreakStreamRawTokensBeforeGuard) {
                    // Sanity-break check 3: emit unverified raw model text before guard inspection
                    emitter.send(SseEmitter.event().name("token").data(Map.of("content", "[UNVERIFIED RAW TOKEN: $9999.00]")));
                }

                AgentRuntime.AgentResponse response = agentRuntime.run(new AgentRuntime.AgentRequest(
                        userId,
                        roles,
                        perms,
                        request.conversationId(),
                        request.message()
                ));

                // Emit tool events
                if (response.toolCalls() != null) {
                    for (var tc : response.toolCalls()) {
                        emitter.send(SseEmitter.event().name("tool").data(Map.of("name", tc.name(), "status", tc.status())));
                    }
                }

                // Emit pending actions
                if (response.pendingActions() != null) {
                    for (var pa : response.pendingActions()) {
                        emitter.send(SseEmitter.event().name("pending_action").data(Map.of(
                                "id", pa.id(), "summary", pa.summary(), "expiresAt", pa.expiresAt()
                        )));
                    }
                }

                // Emit guard status
                boolean replaced = response.guard() != null && response.guard().replacedByFallback();
                emitter.send(SseEmitter.event().name("guard").data(Map.of("replacedByFallback", replaced)));

                // Deliberate safety decision: stream the validated final reply in chunks AFTER the guard passes
                String finalReply = response.reply();
                if (finalReply != null && !finalReply.isEmpty()) {
                    int chunkSize = 20;
                    for (int i = 0; i < finalReply.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReply.length());
                        String chunk = finalReply.substring(i, end);
                        emitter.send(SseEmitter.event().name("token").data(Map.of("content", chunk)));
                    }
                }

                // Emit done event
                emitter.send(SseEmitter.event().name("done").data(Map.of("conversationId", response.conversationId())));
                emitter.complete();
            } catch (ApiException e) {
                sendErrorAndComplete(emitter, e.getErrorCode() != null ? e.getErrorCode().name() : "ERROR", e.getMessage(), userId);
            } catch (Exception e) {
                log.error("Streaming error for user {}: {}", userId, e.getMessage(), e);
                sendErrorAndComplete(emitter, "INTERNAL_ERROR", "Streaming execution failed", userId);
            } finally {
                activeStreams.remove(userId);
            }
        });

        return emitter;
    }

    private void sendErrorAndComplete(SseEmitter emitter, String code, String message, Long userId) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message)));
            emitter.complete();
        } catch (Exception ignored) {
        } finally {
            activeStreams.remove(userId);
        }
    }

    @GetMapping("/conversations")
    @Operation(summary = "Get current user's conversations (paginated)")
    public ResponseEntity<Page<AgentConversationDto>> getConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int effectiveSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by("lastActiveAt").descending());
        return ResponseEntity.ok(agentMemoryService.getConversations(principal.getId(), pageable));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get messages for a conversation (own only)")
    public ResponseEntity<List<AgentMessageDto>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(agentMemoryService.getMessages(id, principal.getId()));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete conversation and messages (own only)")
    public ResponseEntity<Map<String, Object>> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        agentMemoryService.deleteConversation(id, principal.getId());
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }

    @GetMapping("/actions/pending")
    @Operation(summary = "Get user's pending, non-expired actions")
    public ResponseEntity<List<PendingActionProposal>> getPendingActions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(pendingActionService.listPending(principal.getId()));
    }

    @PostMapping("/actions/{id}/confirm")
    @Operation(summary = "Confirm pending action (human-in-the-loop confirmation)")
    public ResponseEntity<Map<String, Object>> confirmAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        Map<String, Object> result = pendingActionService.confirm(id, principal.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/actions/{id}/reject")
    @Operation(summary = "Reject pending action")
    public ResponseEntity<Map<String, Object>> rejectAction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        pendingActionService.reject(id, principal.getId());
        return ResponseEntity.ok(Map.of("status", "REJECTED", "actionId", id));
    }
}
