package com.fitflow.payout.controller;

import com.fitflow.payout.PayoutService;
import com.fitflow.payout.dto.CreatePayoutBatchRequest;
import com.fitflow.payout.dto.PayoutBatchResponse;
import com.fitflow.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final PayoutService payoutService;

    @PostMapping
    @PreAuthorize("hasAuthority('PAYOUT_MANAGE') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayoutBatchResponse> createPayoutBatch(
            @Valid @RequestBody CreatePayoutBatchRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayoutBatchResponse response = payoutService.createDraftPayout(
                request.periodStart(), request.periodEnd(), principal.getId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYOUT_MANAGE') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayoutBatchResponse> getPayoutBatch(@PathVariable Long id) {
        PayoutBatchResponse response = payoutService.getPayout(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PAYOUT_MANAGE') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayoutBatchResponse> approvePayoutBatch(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayoutBatchResponse response = payoutService.approvePayout(id, principal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("hasAuthority('PAYOUT_MANAGE') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PayoutBatchResponse> markPayoutPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        PayoutBatchResponse response = payoutService.markPayoutPaid(id, principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{id}/export.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('PAYOUT_MANAGE') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<String> exportPayoutCsv(@PathVariable Long id) {
        String csv = payoutService.exportPayoutCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payout-" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
