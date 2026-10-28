package com.fitflow.membership.controller;

import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.CreatePlanRequest;
import com.fitflow.membership.dto.PlanDto;
import com.fitflow.membership.dto.UpdatePlanRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final MembershipService membershipService;

    @PostMapping
    @PreAuthorize("hasAuthority('PLAN_EDIT') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PlanDto> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanDto created = membershipService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLAN_EDIT') or hasAuthority('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<PlanDto> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePlanRequest request) {
        PlanDto updated = membershipService.updatePlan(id, request);
        return ResponseEntity.ok(updated);
    }
}
