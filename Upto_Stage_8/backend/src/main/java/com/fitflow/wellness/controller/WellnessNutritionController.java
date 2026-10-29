package com.fitflow.wellness.controller;

import com.fitflow.security.UserPrincipal;
import com.fitflow.wellness.CalorieService;
import com.fitflow.wellness.dto.ClassBurnResponse;
import com.fitflow.wellness.dto.NutritionNumbersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wellness")
@RequiredArgsConstructor
@Tag(name = "Wellness Nutrition", description = "Deterministic calorie burn and daily energy expenditure calculations")
public class WellnessNutritionController {

    private final CalorieService calorieService;

    @GetMapping("/nutrition/numbers")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Get deterministic BMR, TDEE, and daily target calorie estimates")
    public ResponseEntity<NutritionNumbersResponse> getNutritionNumbers(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(calorieService.getNutritionNumbers(principal.getId()));
    }

    @GetMapping("/classes/{classId}/burn")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Estimate calorie burn for a class based on duration, MET value, and member weight")
    public ResponseEntity<ClassBurnResponse> getClassBurn(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long classId) {
        return ResponseEntity.ok(calorieService.getClassBurn(principal.getId(), classId));
    }
}
