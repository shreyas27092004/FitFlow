package com.fitflow.agent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.agents.WellnessCoachAgent;
import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.core.AgentStatusService;
import com.fitflow.agent.entity.CoachSummary;
import com.fitflow.agent.entity.JobRun;
import com.fitflow.agent.guard.NumberGuard;
import com.fitflow.agent.repository.CoachSummaryRepository;
import com.fitflow.agent.repository.JobRunRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.job.JobLockRepository;
import com.fitflow.notification.NotificationRecipientDao;
import com.fitflow.notification.OutboxService;
import com.fitflow.wellness.ConsentService;
import com.fitflow.wellness.WellnessFeedbackService;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import com.fitflow.wellness.model.ConsentPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component("weeklyCoachSummaryJob")
@RequiredArgsConstructor
public class WeeklyCoachSummaryJob {

    public static final String JOB_NAME = "weekly_coach_summary_job";

    private final WellnessFeedbackService wellnessFeedbackService;
    private final CoachSummaryRepository coachSummaryRepository;
    private final JobRunRepository jobRunRepository;
    private final JobLockRepository jobLockRepository;
    private final NotificationRecipientDao recipientDao;
    private final BookingRepository bookingRepository;
    private final ConsentService consentService;
    private final AgentStatusService agentStatusService;
    private final AgentRuntime agentRuntime;
    private final NumberGuard numberGuard;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    @Value("${fitflow.jobs.weekly-coach-summary.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 0 8 * * MON", zone = "${fitflow.display-timezone:Asia/Kolkata}")
    public void runScheduled() {
        if (!enabled) {
            log.debug("weeklyCoachSummaryJob is disabled by configuration");
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
            log.debug("weeklyCoachSummaryJob lock held by another instance. Skipping.");
            return 0;
        }

        int processed = 0;
        int failed = 0;

        try {
            LocalDate lastMonday = LocalDate.now(clock).minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Instant last28Days = startTime.minus(Duration.ofDays(28));

            // Find recipients with emailReminders enabled and active status
            List<NotificationRecipientDao.RecipientInfo> recipients = recipientDao.findActiveMembers();

            // Filter for members with at least 1 booking in last 28 days and emailReminders enabled
            List<NotificationRecipientDao.RecipientInfo> candidateMembers = recipients.stream()
                    .filter(r -> Boolean.TRUE.equals(r.emailReminders()))
                    .filter(r -> bookingRepository.countAttendedBookingsInWindow(r.id(), last28Days, startTime) > 0)
                    .limit(500)
                    .toList();

            log.info("weeklyCoachSummaryJob running for {} candidate members", candidateMembers.size());

            // Process in batches of 50
            int batchSize = 50;
            for (int i = 0; i < candidateMembers.size(); i += batchSize) {
                int end = Math.min(i + batchSize, candidateMembers.size());
                List<NotificationRecipientDao.RecipientInfo> batch = candidateMembers.subList(i, end);

                for (NotificationRecipientDao.RecipientInfo member : batch) {
                    try {
                        processSingleMember(member, lastMonday, startTime);
                        processed++;
                    } catch (Exception e) {
                        failed++;
                        log.warn("Failed to generate coach summary for member id={}: {}", member.id(), e.getMessage());
                    }
                }
            }

            return processed;
        } finally {
            Instant finishTime = clock.instant();
            try {
                JobRun run = JobRun.builder()
                        .name("weeklyCoachSummaryJob")
                        .startedAt(startTime)
                        .finishedAt(finishTime)
                        .processed(processed)
                        .failed(failed)
                        .createdAt(finishTime)
                        .build();
                saveJobRun(run);
            } catch (Exception ex) {
                log.warn("Failed to record job run for weeklyCoachSummaryJob: {}", ex.getMessage());
            }

            try {
                jobLockRepository.releaseLock(JOB_NAME, instanceId);
            } catch (Exception ignored) {}
        }
    }

    private void processSingleMember(NotificationRecipientDao.RecipientInfo member, LocalDate weekStart, Instant now) throws Exception {
        Long memberId = member.id();

        WeeklyProgressResponse progress = wellnessFeedbackService.getWeeklyProgress(memberId, weekStart);

        String templateText = String.format("Last week you attended %d of %d classes. Your current active streak is %d week(s).",
                progress.attendedCount(), progress.bookedCount(), progress.streakWeeks());

        String finalText = templateText;

        // Check if AI rewrite is allowed
        boolean aiConfigured = agentStatusService.getStatus(memberId, Set.of("MEMBER")).configured();
        boolean hasAiConsent = consentService.hasConsent(memberId, ConsentPurpose.AI_ASSISTANT_PROCESSING);

        if (aiConfigured && hasAiConsent) {
            try {
                String promptMsg = String.format("Rewrite this weekly summary warmly using only these numbers: attended=%d, booked=%d, streak=%d",
                        progress.attendedCount(), progress.bookedCount(), progress.streakWeeks());

                AgentRuntime.AgentResponse aiResp = agentRuntime.run(new AgentRuntime.AgentRequest(
                        memberId, Set.of("MEMBER"), Set.of(), null, promptMsg));

                String candidate = aiResp.reply();
                var guardCheck = numberGuard.verifyReply(candidate, promptMsg,
                        List.of(objectMapper.writeValueAsString(progress)),
                        List.of(templateText));

                if (guardCheck.verified() && !candidate.isBlank()) {
                    finalText = candidate;
                }
            } catch (Exception e) {
                log.debug("AI rewrite failed, using deterministic template text: {}", e.getMessage());
                finalText = templateText;
            }
        }

        Map<String, Object> numbersMap = Map.of(
                "sessionsAttended", progress.attendedCount(),
                "sessionsBooked", progress.bookedCount(),
                "streakWeeks", progress.streakWeeks(),
                "estimatedKcalBurned", progress.estimatedKcalBurned() != null ? progress.estimatedKcalBurned() : 0
        );

        String numbersJson = objectMapper.writeValueAsString(numbersMap);

        CoachSummary summary = CoachSummary.builder()
                .memberId(memberId)
                .weekStart(weekStart)
                .summary(numbersJson)
                .numbersJson(numbersJson)
                .summaryText(finalText)
                .createdAt(now)
                .build();

        summary = saveCoachSummary(summary);

        outboxService.append("USER", memberId, "weekly.summary", Map.of(
                "memberId", memberId,
                "summaryId", summary.getId(),
                "summaryText", finalText
        ));
    }

    @Transactional("postgresTransactionManager")
    public CoachSummary saveCoachSummary(CoachSummary summary) {
        return coachSummaryRepository.save(summary);
    }

    @Transactional("postgresTransactionManager")
    public JobRun saveJobRun(JobRun run) {
        return jobRunRepository.save(run);
    }
}
