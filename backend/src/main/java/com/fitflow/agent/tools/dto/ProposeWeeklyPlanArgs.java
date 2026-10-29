package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record ProposeWeeklyPlanArgs(
        @JsonProperty("weekStart") LocalDate weekStart,
        @JsonProperty("sessionIds") List<Long> sessionIds
) {}
