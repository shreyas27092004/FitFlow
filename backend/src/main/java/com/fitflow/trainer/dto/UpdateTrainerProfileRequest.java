package com.fitflow.trainer.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateTrainerProfileRequest(
        String bio,
        List<String> specialties,
        List<String> certifications,
        List<AvailabilitySlotDto> availability,
        Long ratePerSessionPaise,
        @NotNull(message = "Version is required for optimistic locking")
        Integer version
) {}
