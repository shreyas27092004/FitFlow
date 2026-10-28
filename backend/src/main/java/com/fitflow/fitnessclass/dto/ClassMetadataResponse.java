package com.fitflow.fitnessclass.dto;

import java.util.List;

public record ClassMetadataResponse(
        List<String> intensity,
        List<String> impactLevel,
        List<String> contraindicationTags,
        List<String> categories
) {
}
