package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitflow.wellness.model.ConsentPurpose;

import java.util.List;

public record ConsentInfoResponse(
        @JsonProperty("version")
        String version,

        @JsonProperty("text")
        String text,

        @JsonProperty("purposes")
        List<ConsentPurpose> purposes,

        @JsonProperty("granted")
        List<GrantedPurposeDto> granted,

        @JsonProperty("eligible")
        boolean eligible
) {
    @Override
    public String toString() {
        return "ConsentInfoResponse[REDACTED]";
    }
}
