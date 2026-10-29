package com.fitflow.agent.job;

import com.fitflow.agent.entity.JobRun;
import com.fitflow.agent.repository.JobRunRepository;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.common.job.JobLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component("pendingActionExpiryJob")
@RequiredArgsConstructor
public class PendingActionExpiryJob {

    public static final String JOB_NAME = "pending_action_expiry_job";

    private final PendingActionService pendingActionService;
    private final JobRunRepository jobRunRepository;
    private final JobLockRepository jobLockRepository;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    @Value("${fitflow.jobs.pending-action-expiry.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedRate = 60000) // Every minute
    public void runScheduled() {
        if (!enabled) {
            log.debug("pendingActionExpiryJob is disabled by configuration");
            return;
        }
        execute();
    }

    public int execute() {
        Instant startTime = clock.instant();
        Instant expiredBefore = startTime.minusSeconds(60);

        int acquired = 0;
        try {
            acquired = jobLockRepository.acquireLock(JOB_NAME, startTime, instanceId, expiredBefore);
        } catch (Exception e) {
            log.warn("JobLock acquire failed for {}: {}", JOB_NAME, e.getMessage());
        }

        if (acquired == 0) {
            log.debug("pendingActionExpiryJob lock held by another instance. Skipping.");
            return 0;
        }

        int processed = 0;
        int failed = 0;

        try {
            pendingActionService.expireOldActions();
            processed = 1;
            return processed;
        } catch (Exception e) {
            failed++;
            log.error("Failed to expire old pending actions: {}", e.getMessage(), e);
            return 0;
        } finally {
            Instant finishTime = clock.instant();
            try {
                JobRun run = JobRun.builder()
                        .name("pendingActionExpiryJob")
                        .startedAt(startTime)
                        .finishedAt(finishTime)
                        .processed(processed)
                        .failed(failed)
                        .createdAt(finishTime)
                        .build();
                saveJobRun(run);
            } catch (Exception ex) {
                log.warn("Failed to record job run for pendingActionExpiryJob: {}", ex.getMessage());
            }

            try {
                jobLockRepository.releaseLock(JOB_NAME, instanceId);
            } catch (Exception ignored) {}
        }
    }

    @Transactional("postgresTransactionManager")
    public JobRun saveJobRun(JobRun run) {
        return jobRunRepository.save(run);
    }
}
