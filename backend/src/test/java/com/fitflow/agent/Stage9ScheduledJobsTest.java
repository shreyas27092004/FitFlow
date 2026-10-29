package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.core.AgentRuntime;
import com.fitflow.agent.dto.AtRiskMemberDto;
import com.fitflow.agent.entity.CoachSummary;
import com.fitflow.agent.entity.JobRun;
import com.fitflow.agent.guard.NumberGuard;
import com.fitflow.agent.job.AtRiskDetectionJob;
import com.fitflow.agent.job.PendingActionExpiryJob;
import com.fitflow.agent.job.WeeklyCoachSummaryJob;
import com.fitflow.agent.repository.AtRiskFlagRepository;
import com.fitflow.agent.repository.CoachSummaryRepository;
import com.fitflow.agent.repository.JobRunRepository;
import com.fitflow.agent.core.AgentStatusService;
import com.fitflow.agent.service.AtRiskService;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.job.JobLockRepository;
import com.fitflow.notification.NotificationRecipientDao;
import com.fitflow.notification.OutboxService;
import com.fitflow.notification.NotificationRecipientDao.RecipientInfo;
import com.fitflow.wellness.ConsentService;
import com.fitflow.wellness.WellnessFeedbackService;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import com.fitflow.wellness.model.ConsentPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class Stage9ScheduledJobsTest {

    private JobLockRepository jobLockRepository;
    private JobRunRepository jobRunRepository;
    private OutboxService outboxService;
    private Clock fixedClock;
    private ObjectMapper objectMapper;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        jobLockRepository = Mockito.mock(JobLockRepository.class);
        jobRunRepository = Mockito.mock(JobRunRepository.class);
        outboxService = Mockito.mock(OutboxService.class);
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // Lock acquisition succeeds by default
        Mockito.when(jobLockRepository.acquireLock(any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("Job: WeeklyCoachSummaryJob processes active members, verifies NumberGuard, and logs JobRun")
    void weeklyCoachSummaryJobExecution() {
        WellnessFeedbackService wellnessFeedbackService = Mockito.mock(WellnessFeedbackService.class);
        CoachSummaryRepository coachSummaryRepository = Mockito.mock(CoachSummaryRepository.class);
        NotificationRecipientDao recipientDao = Mockito.mock(NotificationRecipientDao.class);
        BookingRepository bookingRepository = Mockito.mock(BookingRepository.class);
        ConsentService consentService = Mockito.mock(ConsentService.class);
        AgentStatusService agentStatusService = Mockito.mock(AgentStatusService.class);
        AgentRuntime agentRuntime = Mockito.mock(AgentRuntime.class);
        NumberGuard numberGuard = new NumberGuard();

        WeeklyCoachSummaryJob job = new WeeklyCoachSummaryJob(
                wellnessFeedbackService,
                coachSummaryRepository,
                jobRunRepository,
                jobLockRepository,
                recipientDao,
                bookingRepository,
                consentService,
                agentStatusService,
                agentRuntime,
                numberGuard,
                outboxService,
                objectMapper,
                fixedClock
        );

        RecipientInfo recipient = new RecipientInfo(
                10L, "alice@fitflow.local", "Alice Smith", "ACTIVE", true, true, false
        );
        Mockito.when(recipientDao.findActiveMembers()).thenReturn(List.of(recipient));
        Mockito.when(bookingRepository.countAttendedBookingsInWindow(eq(10L), any(), any())).thenReturn(2L);
        Mockito.when(consentService.hasConsent(10L, ConsentPurpose.AI_ASSISTANT_PROCESSING)).thenReturn(false);
        Mockito.when(consentService.hasConsent(10L, ConsentPurpose.CALORIE_ESTIMATES)).thenReturn(true);
        Mockito.when(agentStatusService.getStatus(eq(10L), any()))
                .thenReturn(new com.fitflow.agent.dto.AgentStatusResponse("MEMBER", "gemini-2.5", false, List.of(), 50));

        CoachSummary savedSummary = CoachSummary.builder().id(99L).build();
        Mockito.when(coachSummaryRepository.save(any(CoachSummary.class))).thenReturn(savedSummary);

        WeeklyProgressResponse wpr = new WeeklyProgressResponse(
                3, 3, 0, 3, 900, List.of()
        );
        Mockito.when(wellnessFeedbackService.getWeeklyProgress(eq(10L), any())).thenReturn(wpr);

        int processed = job.execute();
        assertEquals(1, processed);

        Mockito.verify(coachSummaryRepository, Mockito.times(1)).save(any(CoachSummary.class));
        Mockito.verify(outboxService, Mockito.times(1)).append(eq("USER"), eq(10L), eq("weekly.summary"), any());
        Mockito.verify(jobRunRepository, Mockito.times(1)).save(any(JobRun.class));
    }

    @Test
    @DisplayName("Job: AtRiskDetectionJob flags at-risk members and publishes retention nudges")
    void atRiskDetectionJobExecution() {
        AtRiskService atRiskService = Mockito.mock(AtRiskService.class);
        AtRiskFlagRepository atRiskFlagRepository = Mockito.mock(AtRiskFlagRepository.class);

        AtRiskDetectionJob job = new AtRiskDetectionJob(
                atRiskService,
                atRiskFlagRepository,
                jobRunRepository,
                jobLockRepository,
                outboxService,
                fixedClock
        );

        AtRiskMemberDto arm = new AtRiskMemberDto(15L, "Bob", List.of("ATTENDANCE_DROP_50_PERCENT"), null);
        Mockito.when(atRiskService.listAtRiskMembers(anyInt())).thenReturn(List.of(arm));
        Mockito.when(atRiskFlagRepository.wasFlaggedSince(eq(15L), any())).thenReturn(false);

        int processed = job.execute();
        assertEquals(1, processed);

        Mockito.verify(atRiskFlagRepository, Mockito.times(1)).save(any());
        Mockito.verify(outboxService, Mockito.times(1)).append(eq("USER"), eq(15L), eq("member.at_risk"), any());
        Mockito.verify(jobRunRepository, Mockito.times(1)).save(any(JobRun.class));
    }

    @Test
    @DisplayName("Job: PendingActionExpiryJob marks expired actions and logs execution")
    void pendingActionExpiryJobExecution() {
        PendingActionService pendingActionService = Mockito.mock(PendingActionService.class);

        PendingActionExpiryJob job = new PendingActionExpiryJob(
                pendingActionService,
                jobRunRepository,
                jobLockRepository,
                fixedClock
        );

        int processed = job.execute();
        assertEquals(1, processed);

        Mockito.verify(pendingActionService, Mockito.times(1)).expireOldActions();
        Mockito.verify(jobRunRepository, Mockito.times(1)).save(any(JobRun.class));
    }
}
