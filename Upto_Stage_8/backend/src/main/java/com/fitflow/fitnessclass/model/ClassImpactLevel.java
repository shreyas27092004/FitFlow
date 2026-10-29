package com.fitflow.fitnessclass.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitflow.common.error.ApiException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ClassImpactLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static final Set<String> ALLOWED_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @JsonCreator
    public static ClassImpactLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ClassImpactLevel impact : values()) {
            if (impact.name().equalsIgnoreCase(value.trim())) {
                return impact;
            }
        }
        throw ApiException.badRequest("Invalid impact level: '" + value + "'. Allowed values: " + ALLOWED_NAMES);
    }
}
