package com.fitflow.fitnessclass.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitflow.common.error.ApiException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ClassCategory {
    YOGA,
    PILATES,
    HIIT,
    STRENGTH,
    CARDIO,
    DANCE,
    MOBILITY,
    BOXING;

    public static final Set<String> ALLOWED_NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @JsonCreator
    public static ClassCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ClassCategory category : values()) {
            if (category.name().equalsIgnoreCase(value.trim())) {
                return category;
            }
        }
        throw ApiException.badRequest("Invalid category: '" + value + "'. Allowed categories: " + ALLOWED_NAMES);
    }
}
