package com.fitflow.fitnessclass.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitflow.common.error.ApiException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ContraindicationTag {
    HIGH_IMPACT("high_impact"),
    HEAVY_LIFTING("heavy_lifting"),
    DEEP_FLEXION("deep_flexion"),
    HEATED_ROOM("heated_room"),
    INVERSION("inversion"),
    HIGH_CARDIO("high_cardio"),
    BALANCE_RISK("balance_risk");

    private final String value;

    ContraindicationTag(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static final Set<String> ALLOWED_VALUES = Arrays.stream(values())
            .map(ContraindicationTag::getValue)
            .collect(Collectors.toUnmodifiableSet());

    @JsonCreator
    public static ContraindicationTag fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ContraindicationTag tag : values()) {
            if (tag.value.equalsIgnoreCase(value.trim())) {
                return tag;
            }
        }
        throw ApiException.badRequest("Invalid contraindication tag: '" + value + "'. Allowed values: " + ALLOWED_VALUES);
    }
}
