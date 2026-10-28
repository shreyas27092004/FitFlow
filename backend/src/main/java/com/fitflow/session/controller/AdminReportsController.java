package com.fitflow.session.controller;

import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.UtilizationReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports", description = "Administrative Utilization and Analytics Reports")
public class AdminReportsController {

    private final ClassSessionService sessionService;

    @GetMapping("/utilization")
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('SUPER_ADMIN')")
    @Operation(summary = "Get class and weekly session utilization report")
    public ResponseEntity<UtilizationReportResponse> getUtilization(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return ResponseEntity.ok(sessionService.getUtilizationReport(from, to));
    }
}
