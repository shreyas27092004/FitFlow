package com.fitflow.payout.controller;

import com.fitflow.payout.PayoutService;
import com.fitflow.payout.dto.TrainerEarningsResponse;
import com.fitflow.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/trainer/me/earnings")
@RequiredArgsConstructor
public class TrainerEarningsController {

    private final PayoutService payoutService;

    @GetMapping
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<TrainerEarningsResponse> getMyEarnings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TrainerEarningsResponse response = payoutService.getTrainerEarnings(principal.getId(), from, to);
        return ResponseEntity.ok(response);
    }
}
