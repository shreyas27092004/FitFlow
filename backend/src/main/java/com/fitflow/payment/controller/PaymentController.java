package com.fitflow.payment.controller;

import com.fitflow.common.pagination.PageRequestDto;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.payment.PaymentService;
import com.fitflow.payment.dto.ConfirmPaymentRequest;
import com.fitflow.payment.dto.InvoiceDetailResponse;
import com.fitflow.payment.dto.MemberPaymentSummaryDto;
import com.fitflow.payment.dto.PaymentDetailResponse;
import com.fitflow.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Member payment confirmation, history, and invoices")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments/{paymentId}/confirm")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Confirm a payment intent (Phase 2)")
    public ResponseEntity<PaymentDetailResponse> confirmPayment(
            @PathVariable Long paymentId,
            @RequestBody(required = false) ConfirmPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        var payload = (request != null && request.clientPayload() != null) ?
                request.clientPayload() : java.util.Map.<String, Object>of();

        PaymentDetailResponse response = paymentService.confirmPayment(paymentId, principal.getId(), payload);
        if (response.status() == com.fitflow.payment.model.PaymentStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get payment intent status")
    public ResponseEntity<PaymentDetailResponse> getPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId, principal.getId()));
    }

    @GetMapping("/me/payments")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get member payments and refunds history")
    public ResponseEntity<PageResponse<MemberPaymentSummaryDto>> getMyPayments(
            @Valid PageRequestDto pageRequest,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<MemberPaymentSummaryDto> page = paymentService.getMemberPayments(
                principal.getId(),
                PageRequest.of(pageRequest.page(), pageRequest.size())
        );
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @GetMapping("/me/invoices/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get member invoice details")
    public ResponseEntity<InvoiceDetailResponse> getMyInvoice(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(paymentService.getInvoice(id, principal.getId()));
    }
}
