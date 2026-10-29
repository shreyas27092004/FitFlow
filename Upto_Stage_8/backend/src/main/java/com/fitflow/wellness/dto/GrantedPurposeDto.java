package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.ConsentPurpose;

import java.time.Instant;

public record GrantedPurposeDto(
        @JsonProperty("purpose")
        ConsentPurpose purpose,

        @JsonProperty("grantedAt")
        Instant grantedAt
) {
    @Override
    public String toString() {
        return "GrantedPurposeDto[REDACTED]";
    }
}
