package com.fitflow.notification;

import com.fitflow.booking.SessionReminderJob;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.entity.BookingReminder;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.BookingReminderRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.job.JobLockRepository;
import com.fitflow.membership.MembershipExpiringJob;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipNotice;
import com.fitflow.membership.entity.MembershipNoticeId;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.repository.MembershipNoticeRepository;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledJobsTest {

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");
    private Clock clock;
    private JobLockRepository jobLockRepository;
    private OutboxService outboxService;

    // Booking job dependencies
    private BookingRepository bookingRepository;
    private BookingReminderRepository bookingReminderRepository;
    private SessionReminderJob sessionReminderJob;

    // Membership job dependencies
    private MembershipRepository membershipRepository;
    private MembershipNoticeRepository membershipNoticeRepository;
    private MembershipPlanRepository membershipPlanRepository;
    private MembershipExpiringJob membershipExpiringJob;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        jobLockRepository = mock(JobLockRepository.class);
        outboxService = mock(OutboxService.class);

        // Always grant lock in mock
        when(jobLockRepository.acquireLock(anyString(), any(), anyString(), any())).thenReturn(1);

        // Session Reminder setup
        bookingRepository = mock(BookingRepository.class);
        bookingReminderRepository = mock(BookingReminderRepository.class);
        sessionReminderJob = new SessionReminderJob(
                bookingRepository,
                bookingReminderRepository,
                outboxService,
                jobLockRepository,
                clock
        );

        // Membership Expiring setup
        membershipRepository = mock(MembershipRepository.class);
        membershipNoticeRepository = mock(MembershipNoticeRepository.class);
        membershipPlanRepository = mock(MembershipPlanRepository.class);
        membershipExpiringJob = new MembershipExpiringJob(
                membershipRepository,
                membershipNoticeRepository,
                membershipPlanRepository,
                outboxService,
                jobLockRepository,
                clock
        );
    }

    @Test
    @DisplayName("SessionReminderJob generates one reminder per booking even if job runs 10 times")
    void testSessionReminderJobOnlyOncePerBooking() {
        Booking booking = Booking.builder()
                .id(777L)
                .memberId(42L)
                .sessionId(101L)
                .status(BookingStatus.CONFIRMED)
                .build();

        Instant windowStart = baseTime.plus(Duration.ofHours(23));
        Instant windowEnd = baseTime.plus(Duration.ofHours(25));

        Map<Long, BookingReminder> savedReminders = new ConcurrentHashMap<>();
        when(bookingReminderRepository.existsById(777L)).thenAnswer(inv -> savedReminders.containsKey(777L));
        when(bookingReminderRepository.save(any(BookingReminder.class))).thenAnswer(inv -> {
            BookingReminder r = inv.getArgument(0);
            savedReminders.put(r.getBookingId(), r);
            return r;
        });

        when(bookingRepository.findConfirmedBookingsNeedingReminder(windowStart, windowEnd))
                .thenAnswer(inv -> savedReminders.containsKey(777L) ? List.of() : List.of(booking));

        // Run the job 10 times
        for (int i = 0; i < 10; i++) {
            sessionReminderJob.processReminders();
        }

        // Must have saved reminder exactly once
        assertEquals(1, savedReminders.size());
        verify(outboxService, times(1)).append(
                eq("BOOKING"), eq(777L), eq("session.reminder"), any()
        );
    }

    @Test
    @DisplayName("SessionReminderJob checks window boundaries: exactly 23h to 25h from now")
    void testSessionReminderWindowBoundaries() {
        sessionReminderJob.processReminders();

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(bookingRepository).findConfirmedBookingsNeedingReminder(startCaptor.capture(), endCaptor.capture());

        assertEquals(baseTime.plus(Duration.ofHours(23)), startCaptor.getValue());
        assertEquals(baseTime.plus(Duration.ofHours(25)), endCaptor.getValue());
    }

    @Test
    @DisplayName("MembershipExpiringJob generates one expiring notice per membership even if job runs 10 times")
    void testMembershipExpiringJobOnlyOncePerMembership() {
        Membership membership = Membership.builder()
                .id(888L)
                .memberId(52L)
                .planId(2L)
                .status(MembershipStatus.ACTIVE)
                .endsAt(baseTime.plus(Duration.ofHours(72)))
                .build();

        MembershipPlan plan = MembershipPlan.builder()
                .id(2L)
                .code("PRO")
                .name("Pro Monthly")
                .build();

        when(membershipPlanRepository.findById(2L)).thenReturn(Optional.of(plan));

        Instant windowStart = baseTime.plus(Duration.ofHours(71));
        Instant windowEnd = baseTime.plus(Duration.ofHours(73));

        Map<MembershipNoticeId, MembershipNotice> savedNotices = new ConcurrentHashMap<>();
        MembershipNoticeId expectedId = new MembershipNoticeId(888L, MembershipExpiringJob.NOTICE_KIND_3_DAYS);

        when(membershipNoticeRepository.existsById(expectedId)).thenAnswer(inv -> savedNotices.containsKey(expectedId));
        when(membershipNoticeRepository.save(any(MembershipNotice.class))).thenAnswer(inv -> {
            MembershipNotice n = inv.getArgument(0);
            savedNotices.put(n.getId(), n);
            return n;
        });

        when(membershipRepository.findActiveExpiringMembershipsNeedingNotice(windowStart, windowEnd, MembershipExpiringJob.NOTICE_KIND_3_DAYS))
                .thenAnswer(inv -> savedNotices.containsKey(expectedId) ? List.of() : List.of(membership));

        // Run the job 10 times
        for (int i = 0; i < 10; i++) {
            membershipExpiringJob.processExpiringMemberships();
        }

        assertEquals(1, savedNotices.size());
        verify(outboxService, times(1)).append(
                eq("MEMBERSHIP"), eq(888L), eq("membership.expiring"), any()
        );
    }

    @Test
    @DisplayName("MembershipExpiringJob checks window boundaries: exactly 71h to 73h from now (3 days +-1h)")
    void testMembershipExpiringWindowBoundaries() {
        membershipExpiringJob.processExpiringMemberships();

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(membershipRepository).findActiveExpiringMembershipsNeedingNotice(
                startCaptor.capture(), endCaptor.capture(), eq(MembershipExpiringJob.NOTICE_KIND_3_DAYS)
        );

        assertEquals(baseTime.plus(Duration.ofHours(71)), startCaptor.getValue());
        assertEquals(baseTime.plus(Duration.ofHours(73)), endCaptor.getValue());
    }
}
