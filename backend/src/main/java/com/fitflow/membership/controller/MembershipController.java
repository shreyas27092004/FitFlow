package com.fitflow.membership.controller;

import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangeMembershipRequest;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.membership.dto.SubscribeRequest;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/me/membership")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberMembershipSummaryResponse> getMyMembership(
            @AuthenticationPrincipal UserPrincipal principal) {
        MemberMembershipSummaryResponse summary = membershipService.getMemberMembership(principal.getId());
        return ResponseEntity.ok(summary);
    }

    @PostMapping(path = {"/memberships/change-preview", "/me/membership/change-preview"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChangePreviewResponse> changePreview(
            @Valid @RequestBody ChangePreviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ChangePreviewResponse preview = membershipService.changePreview(principal.getId(), request);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/memberships/subscribe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentIntentResponse> subscribe(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubscribeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : UUID.randomUUID().toString();
        PaymentIntentResponse intent = membershipService.subscribe(principal.getId(), key, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(intent);
    }

    @PostMapping("/memberships/change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changeMembership(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChangeMembershipRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : UUID.randomUUID().toString();
        Object result = membershipService.changeMembership(principal.getId(), key, request);
        if (result instanceof PaymentIntentResponse intent) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(intent);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/memberships/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> cancelMembership(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : UUID.randomUUID().toString();
        Map<String, Object> result = membershipService.cancelMembership(principal.getId(), key);
        return ResponseEntity.ok(result);
    }
}
