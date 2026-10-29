package com.fitflow.agent.controller;

import com.fitflow.agent.job.AtRiskDetectionJob;
import com.fitflow.agent.job.PendingActionExpiryJob;
import com.fitflow.agent.job.WeeklyCoachSummaryJob;
import com.fitflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dev/jobs")
@Profile("dev")
@RequiredArgsConstructor
public class DevJobController {

    private final WeeklyCoachSummaryJob weeklyCoachSummaryJob;
    private final AtRiskDetectionJob atRiskDetectionJob;
    private final PendingActionExpiryJob pendingActionExpiryJob;

    @PostMapping("/{name}/run")
    public ResponseEntity<Map<String, Object>> runJob(@PathVariable String name) {
        log.info("Dev trigger requested for job: {}", name);
        int processed;
        switch (name) {
            case "weeklyCoachSummaryJob" -> processed = weeklyCoachSummaryJob.execute();
            case "atRiskDetectionJob" -> processed = atRiskDetectionJob.execute();
            case "pendingActionExpiryJob" -> processed = pendingActionExpiryJob.execute();
            default -> throw ApiException.notFound("Unknown job: " + name);
        }

        return ResponseEntity.ok(Map.of(
                "job", name,
                "processed", processed,
                "status", "COMPLETED"
        ));
    }
}
