package com.fitflow.agent.job;

import com.fitflow.agent.service.AtRiskService;
import com.fitflow.agent.dto.AtRiskMemberDto;
import com.fitflow.agent.entity.AtRiskFlag;
import com.fitflow.agent.entity.JobRun;
import com.fitflow.agent.repository.AtRiskFlagRepository;
import com.fitflow.agent.repository.JobRunRepository;
import com.fitflow.common.job.JobLockRepository;
import com.fitflow.notification.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("atRiskDetectionJob")
@RequiredArgsConstructor
public class AtRiskDetectionJob {

    public static final String JOB_NAME = "at_risk_detection_job";

    private final AtRiskService atRiskService;
    private final AtRiskFlagRepository atRiskFlagRepository;
    private final JobRunRepository jobRunRepository;
    private final JobLockRepository jobLockRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    @Value("${fitflow.jobs.at-risk-detection.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 0 2 * * *", zone = "${fitflow.display-timezone:Asia/Kolkata}")
    public void runScheduled() {
        if (!enabled) {
            log.debug("atRiskDetectionJob is disabled by configuration");
            return;
        }
        execute();
    }

    public int execute() {
        Instant startTime = clock.instant();
        Instant expiredBefore = startTime.minusSeconds(300);

        int acquired = 0;
        try {
            acquired = jobLockRepository.acquireLock(JOB_NAME, startTime, instanceId, expiredBefore);
        } catch (Exception e) {
            log.warn("JobLock acquire failed for {}: {}", JOB_NAME, e.getMessage());
        }

        if (acquired == 0) {
            log.debug("atRiskDetectionJob lock held by another instance. Skipping.");
            return 0;
        }

        int processed = 0;
        int failed = 0;

        try {
            LocalDate today = LocalDate.now(clock);
            LocalDate deduplicateSince = today.minusDays(14);

            List<AtRiskMemberDto> atRiskMembers = atRiskService.listAtRiskMembers(100);
            log.info("atRiskDetectionJob identified {} at-risk member(s)", atRiskMembers.size());

            for (AtRiskMemberDto member : atRiskMembers) {
                try {
                    boolean recentlyFlagged = atRiskFlagRepository.wasFlaggedSince(member.memberId(), deduplicateSince);
                    if (recentlyFlagged) {
                        log.debug("Member {} was flagged within last 14 days. Skipping duplicate flag.", member.memberId());
                        continue;
                    }

                    // Save flag
                    AtRiskFlag flag = AtRiskFlag.builder()
                            .memberId(member.memberId())
                            .flaggedOn(today)
                            .createdAt(startTime)
                            .build();
                    saveAtRiskFlag(flag);

                    // Emit event member.at_risk {memberId, reasonCodes}
                    outboxService.append("USER", member.memberId(), "member.at_risk", Map.of(
                            "memberId", member.memberId(),
                            "reasonCodes", member.reasonCodes(),
                            "flaggedOn", today.toString()
                    ));

                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Failed to flag at-risk member id={}: {}", member.memberId(), e.getMessage());
                }
            }

            return processed;
        } finally {
            Instant finishTime = clock.instant();
            try {
                JobRun run = JobRun.builder()
                        .name("atRiskDetectionJob")
                        .startedAt(startTime)
                        .finishedAt(finishTime)
                        .processed(processed)
                        .failed(failed)
                        .createdAt(finishTime)
                        .build();
                saveJobRun(run);
            } catch (Exception ex) {
                log.warn("Failed to record job run for atRiskDetectionJob: {}", ex.getMessage());
            }

            try {
                jobLockRepository.releaseLock(JOB_NAME, instanceId);
            } catch (Exception ignored) {}
        }
    }

    @Transactional("postgresTransactionManager")
    public AtRiskFlag saveAtRiskFlag(AtRiskFlag flag) {
        return atRiskFlagRepository.save(flag);
    }

    @Transactional("postgresTransactionManager")
    public JobRun saveJobRun(JobRun run) {
        return jobRunRepository.save(run);
    }
}
