package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DraftClassMetadataArgs(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description
) {}
