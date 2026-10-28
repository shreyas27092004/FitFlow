package com.fitflow.trainer.dto;

import java.util.List;

public record TrainerPublicResponse(
        Long id,
        String fullName,
        String bio,
        List<String> specialties
) {}
