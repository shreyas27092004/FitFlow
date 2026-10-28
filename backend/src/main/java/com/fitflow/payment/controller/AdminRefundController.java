package com.fitflow.payment.controller;

import com.fitflow.common.pagination.PageRequestDto;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.payment.PaymentService;
import com.fitflow.payment.dto.AdminRefundResponse;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@Tag(name = "Admin Refunds", description = "Administrative refund approvals and governance")
public class AdminRefundController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('REFUND_APPROVE', 'SUPER_ADMIN')")
    @Operation(summary = "List refunds by status")
    public ResponseEntity<PageResponse<AdminRefundResponse>> getRefunds(
            @RequestParam(required = false) RefundStatus status,
            @Valid PageRequestDto pageRequest
    ) {
        Page<AdminRefundResponse> page = paymentService.getRefunds(
                status,
                PageRequest.of(pageRequest.page(), pageRequest.size())
        );
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('REFUND_APPROVE', 'SUPER_ADMIN')")
    @Operation(summary = "Approve a pending refund")
    public ResponseEntity<AdminRefundResponse> approveRefund(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AdminRefundResponse approved = paymentService.approveRefund(id, principal.getId());
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('REFUND_APPROVE', 'SUPER_ADMIN')")
    @Operation(summary = "Reject a pending refund")
    public ResponseEntity<AdminRefundResponse> rejectRefund(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String reason = body != null ? body.getOrDefault("reason", "Admin rejected") : "Admin rejected";
        AdminRefundResponse rejected = paymentService.rejectRefund(id, principal.getId(), reason);
        return ResponseEntity.ok(rejected);
    }
}
