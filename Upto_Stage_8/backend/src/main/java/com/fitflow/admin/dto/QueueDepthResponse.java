package com.fitflow.admin.dto;

import java.util.List;

public record QueueDepthResponse(
        String mode,
        List<QueueInfo> queues
) {
    public record QueueInfo(
            String name,
            long messageCount,
            long consumerCount
    ) {}
}
