package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ProposeRetentionOfferArgs(
        @JsonProperty("memberId")
        @JsonAlias({"memberId", "targetMemberId"})
        Long targetMemberId,

        @JsonProperty("offerType")
        String offerType
) {}
