package com.fitflow.payment.gateway.razorpay.controller;

import com.fitflow.payment.gateway.razorpay.RazorpayWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Webhooks", description = "Public webhook ingestion endpoint for Razorpay gateway events")
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    @PostMapping(value = "/razorpay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ingest Razorpay webhook event with HMAC-SHA256 signature verification")
    public ResponseEntity<Map<String, String>> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId,
            @RequestBody byte[] rawBody
    ) {
        // 1. Signature Verification
        if (!webhookService.isSignatureValid(rawBody, signature)) {
            log.warn("Razorpay webhook signature verification failed for eventId={}", eventId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Deduplication check
        if (webhookService.isDuplicateEvent(eventId)) {
            log.info("Razorpay duplicate webhook event ignored for eventId={}", eventId);
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        // 3. Process webhook within 5 seconds without outbound HTTP calls
        webhookService.processWebhook(rawBody, eventId);

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
