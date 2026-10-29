package com.fitflow.agent.tools.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ProposeAddSessionArgs(
        @JsonProperty("classId")
        Long classId,

        @JsonProperty("trainerId")
        @JsonAlias({"trainerId", "assignedTrainerId"})
        Long assignedTrainerId,

        @JsonProperty("startsAt")
        Instant startsAt,

        @JsonProperty("room")
        String room
) {}
