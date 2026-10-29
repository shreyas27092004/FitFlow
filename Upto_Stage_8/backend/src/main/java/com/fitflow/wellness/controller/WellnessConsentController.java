package com.fitflow.wellness.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.wellness.ConsentService;
import com.fitflow.wellness.dto.ConsentInfoResponse;
import com.fitflow.wellness.dto.GrantConsentRequest;
import com.fitflow.wellness.model.ConsentPurpose;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wellness/consent")
@RequiredArgsConstructor
@Tag(name = "Wellness Consent", description = "Member health data consent management under DPDP Act")
public class WellnessConsentController {

    private final ConsentService consentService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get consent text, available purposes, current grants, and 18+ eligibility")
    public ResponseEntity<ConsentInfoResponse> getConsentInfo(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(consentService.getConsentInfo(principal.getId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Grant health data processing consent for specified purposes")
    public ResponseEntity<Void> grantConsent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GrantConsentRequest request) {
        consentService.grantConsent(principal.getId(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{purpose}")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Revoke health data processing consent for a specific purpose")
    public ResponseEntity<Void> revokeConsent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable ConsentPurpose purpose) {
        consentService.revokeConsent(principal.getId(), purpose);
        return ResponseEntity.noContent().build();
    }
}
