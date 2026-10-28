package com.fitflow.trainer.dto;

import java.util.List;

public record TrainerProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String bio,
        List<String> specialties,
        List<String> certifications,
        Long ratePerSessionPaise,
        List<AvailabilitySlotDto> availability,
        Boolean active,
        Integer version
) {}
