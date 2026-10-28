package com.fitflow.admin.dto;

public record DlqReplayResponse(
        String queue,
        int replayedCount
) {}
