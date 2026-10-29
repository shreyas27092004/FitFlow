package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record GetMissedSessionsCatchupArgs(
        @JsonProperty("weekStart") LocalDate weekStart
) {}
