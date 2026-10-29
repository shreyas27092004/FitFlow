package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GetMyBookingsArgs(
        @JsonProperty("status") String status,
        @JsonProperty("from") Instant from,
        @JsonProperty("to") Instant to
) {}
