package com.fitflow.membership.controller;

import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.PlanDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final MembershipService membershipService;

    @GetMapping
    public ResponseEntity<List<PlanDto>> getActivePlans() {
        return ResponseEntity.ok(membershipService.getActivePlans());
    }
}
