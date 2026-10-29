package com.fitflow.fitnessclass.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitflow.common.error.ApiException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ClassIntensity {
    LOW,
    MODERATE,
    HIGH;

    public static final Set<String> ALLOWED_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @JsonCreator
    public static ClassIntensity fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ClassIntensity intensity : values()) {
            if (intensity.name().equalsIgnoreCase(value.trim())) {
                return intensity;
            }
        }
        throw ApiException.badRequest("Invalid intensity: '" + value + "'. Allowed values: " + ALLOWED_NAMES);
    }
}
