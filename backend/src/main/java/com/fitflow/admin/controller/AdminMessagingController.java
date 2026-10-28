package com.fitflow.admin.controller;

import com.fitflow.admin.AdminMessagingService;
import com.fitflow.admin.dto.DlqReplayResponse;
import com.fitflow.admin.dto.OutboxEventSummaryResponse;
import com.fitflow.admin.dto.QueueDepthResponse;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/messaging")
@RequiredArgsConstructor
@Tag(name = "Admin Messaging", description = "Endpoints for managing the outbox, queues, and dead-letter queue replays")
public class AdminMessagingController {

    private final AdminMessagingService messagingService;

    @GetMapping("/outbox")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "List outbox events with status and retry metadata (payloads are omitted)")
    public ResponseEntity<PageResponse<OutboxEventSummaryResponse>> getOutbox(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(messagingService.getOutboxEvents(status, page, size));
    }

    @PostMapping("/outbox/{id}/retry")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Retry a FAILED outbox event")
    public ResponseEntity<OutboxEventSummaryResponse> retryOutboxEvent(@PathVariable Long id) {
        return ResponseEntity.ok(messagingService.retryOutboxEvent(id));
    }

    @GetMapping("/queues")
    @PreAuthorize("@perm.hasAny('SETTINGS_EDIT', 'SUPER_ADMIN')")
    @Operation(summary = "Get queue and DLQ depth status")
    public ResponseEntity<QueueDepthResponse> getQueues() {
        return ResponseEntity.ok(messagingService.getQueues());
    }

    @PostMapping("/dlq/{queue}/replay")
    @PreAuthorize("@perm.has('SUPER_ADMIN')")
    @Operation(summary = "Replay messages from a DLQ back to its primary queue (SUPER_ADMIN only)")
    public ResponseEntity<DlqReplayResponse> replayDlq(
            @AuthenticationPrincipal UserPrincipal actor,
            @PathVariable String queue,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(messagingService.replayDlq(actor, queue, limit));
    }
}
