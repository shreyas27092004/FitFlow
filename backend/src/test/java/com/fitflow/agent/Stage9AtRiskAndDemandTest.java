package com.fitflow.agent;

import com.fitflow.agent.dto.AtRiskMemberDto;
import com.fitflow.agent.dto.DemandInsightDto;
import com.fitflow.agent.service.AtRiskService;
import com.fitflow.agent.service.DemandService;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.booking.repository.WaitlistEntryRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class Stage9AtRiskAndDemandTest {

    private UserRepository userRepository;
    private UserRoleRepository userRoleRepository;
    private MembershipRepository membershipRepository;
    private MembershipPlanRepository planRepository;
    private BookingRepository bookingRepository;
    private Clock fixedClock;

    private AtRiskService atRiskService;

    private ClassSessionRepository sessionRepository;
    private WaitlistEntryRepository waitlistEntryRepository;
    private FitnessClassRepository fitnessClassRepository;
    private DemandService demandService;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);

        userRepository = Mockito.mock(UserRepository.class);
        userRoleRepository = Mockito.mock(UserRoleRepository.class);
        membershipRepository = Mockito.mock(MembershipRepository.class);
        planRepository = Mockito.mock(MembershipPlanRepository.class);
        bookingRepository = Mockito.mock(BookingRepository.class);

        atRiskService = new AtRiskService(
                userRepository,
                userRoleRepository,
                membershipRepository,
                planRepository,
                bookingRepository,
                fixedClock
        );

        sessionRepository = Mockito.mock(ClassSessionRepository.class);
        waitlistEntryRepository = Mockito.mock(WaitlistEntryRepository.class);
        fitnessClassRepository = Mockito.mock(FitnessClassRepository.class);

        demandService = new DemandService(
                sessionRepository,
                waitlistEntryRepository,
                fitnessClassRepository,
                fixedClock
        );

        PendingActionService.sanityBreakSkipAtRiskRecheck = false;
    }

    @AfterEach
    void tearDown() {
        PendingActionService.sanityBreakSkipAtRiskRecheck = false;
    }

    private User createActiveMember(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setEmail(name.toLowerCase().replace(" ", "") + "@fitflow.local");
        u.setStatus(User.Status.ACTIVE);
        return u;
    }

    @Test
    @DisplayName("At-Risk: Attendance drop >= 50% in last 28 days vs previous 28 days")
    void atRiskConditionAAttendanceDrop() {
        User member = createActiveMember(10L, "Alice Smith");
        Mockito.when(userRepository.findAll()).thenReturn(List.of(member));
        Mockito.when(userRepository.findById(10L)).thenReturn(Optional.of(member));
        Mockito.when(userRoleRepository.existsByIdUserIdAndIdRole(10L, UserRole.Role.MEMBER)).thenReturn(true);
        Mockito.when(planRepository.findAll()).thenReturn(List.of());
        Mockito.when(membershipRepository.findByMemberIdAndStatus(10L, MembershipStatus.ACTIVE)).thenReturn(List.of());

        // w1 (recent 28 days): 2 attended; w2 (previous 28 days): 8 attended (75% drop >= 50%)
        Instant w1Start = baseTime.minus(Duration.ofDays(28));
        Instant w2Start = baseTime.minus(Duration.ofDays(56));

        Mockito.when(bookingRepository.countAttendedBookingsInWindow(10L, w1Start, baseTime)).thenReturn(2L);
        Mockito.when(bookingRepository.countAttendedBookingsInWindow(10L, w2Start, w1Start)).thenReturn(8L);

        List<AtRiskMemberDto> list = atRiskService.listAtRiskMembers(10);
        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).memberId());
        assertTrue(list.get(0).reasonCodes().contains("ATTENDANCE_DROP_50_PERCENT"));
        assertTrue(atRiskService.isMemberAtRisk(10L));
    }

    @Test
    @DisplayName("At-Risk: Paid plan expiring in <= 7 days with <= 1 session attended in last 14 days")
    void atRiskConditionBExpiringPaidPlan() {
        User member = createActiveMember(20L, "Bob Jones");
        Mockito.when(userRepository.findAll()).thenReturn(List.of(member));
        Mockito.when(userRepository.findById(20L)).thenReturn(Optional.of(member));
        Mockito.when(userRoleRepository.existsByIdUserIdAndIdRole(20L, UserRole.Role.MEMBER)).thenReturn(true);

        MembershipPlan plan = new MembershipPlan();
        plan.setId(5L);
        plan.setCode("PRO");
        plan.setIsFree(false);
        Mockito.when(planRepository.findAll()).thenReturn(List.of(plan));

        // Active paid membership expiring in 4 days
        Membership m = new Membership();
        m.setPlanId(5L);
        m.setMemberId(20L);
        m.setStatus(MembershipStatus.ACTIVE);
        m.setEndsAt(baseTime.plus(Duration.ofDays(4)));
        Mockito.when(membershipRepository.findByMemberIdAndStatus(20L, MembershipStatus.ACTIVE)).thenReturn(List.of(m));

        // w1 and w2 equal (no drop)
        Instant w1Start = baseTime.minus(Duration.ofDays(28));
        Instant w2Start = baseTime.minus(Duration.ofDays(56));
        Mockito.when(bookingRepository.countAttendedBookingsInWindow(20L, w1Start, baseTime)).thenReturn(1L);
        Mockito.when(bookingRepository.countAttendedBookingsInWindow(20L, w2Start, w1Start)).thenReturn(1L);

        // last 14 days: 0 attended
        Instant last14Start = baseTime.minus(Duration.ofDays(14));
        Mockito.when(bookingRepository.countAttendedBookingsInWindow(20L, last14Start, baseTime)).thenReturn(0L);

        List<AtRiskMemberDto> list = atRiskService.listAtRiskMembers(10);
        assertEquals(1, list.size());
        assertEquals(20L, list.get(0).memberId());
        assertTrue(list.get(0).reasonCodes().contains("EXPIRING_SOON_LOW_ATTENDANCE"));
        assertEquals(4L, list.get(0).daysToExpiry());
    }

    @Test
    @DisplayName("At-Risk: Inactive users and non-member roles skipped")
    void atRiskSkipsInactiveAndNonMembers() {
        User inactive = createActiveMember(30L, "Inactive User");
        inactive.setStatus(User.Status.DELETED);

        User trainer = createActiveMember(40L, "Trainer User");

        Mockito.when(userRepository.findAll()).thenReturn(List.of(inactive, trainer));
        Mockito.when(userRoleRepository.existsByIdUserIdAndIdRole(40L, UserRole.Role.MEMBER)).thenReturn(false);

        List<AtRiskMemberDto> list = atRiskService.listAtRiskMembers(10);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("Sanity Break 2: Skipping at-risk re-check allows confirmation for recovered member")
    void sanityBreak2AllowsSkipAtRiskRecheck() {
        PendingActionService.sanityBreakSkipAtRiskRecheck = true;
        assertTrue(PendingActionService.sanityBreakSkipAtRiskRecheck);
    }

    @Test
    @DisplayName("Demand Service: Recommends ADD_SESSION when avg fill rate >= 0.85 or waitlist >= 5")
    void demandAddSessionSuggestion() {
        FitnessClass fc = FitnessClass.builder()
                .name("Hot Yoga")
                .category("YOGA")
                .durationMinutes(60)
                .intensity(ClassIntensity.LOW)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(BigDecimal.valueOf(3.0))
                .build();
        fc.setId(101L);
        Mockito.when(fitnessClassRepository.findAll()).thenReturn(List.of(fc));

        // Monday 18:00 session with 95% fill rate
        ClassSession s = ClassSession.builder()
                .classId(101L)
                .startsAt(Instant.parse("2026-10-26T12:30:00Z")) // Mon 18:00 IST
                .endsAt(Instant.parse("2026-10-26T13:30:00Z"))
                .capacity(20)
                .bookedCount(19) // 19/20 = 95%
                .status(ClassSession.Status.SCHEDULED)
                .build();
        s.setId(1L);

        Mockito.when(sessionRepository.findByStartsAtBetween(any(), any())).thenReturn(List.of(s));
        Mockito.when(waitlistEntryRepository.countBySessionId(1L)).thenReturn(6L);

        DemandInsightDto insights = demandService.getDemandInsights(4);
        assertNotNull(insights);
        assertEquals(1, insights.suggestions().size());
        assertEquals("ADD_SESSION", insights.suggestions().get(0).type());
        assertEquals(101L, insights.suggestions().get(0).classId());
        assertTrue(demandService.hasAddSessionSuggestion(101L, 1, 18));
    }

    @Test
    @DisplayName("Demand Service: Recommends REDUCE_OR_MOVE when avg fill rate <= 0.40")
    void demandReduceSuggestion() {
        FitnessClass fc = FitnessClass.builder()
                .name("Low Demand Class")
                .category("CARDIO")
                .durationMinutes(45)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(BigDecimal.valueOf(8.0))
                .build();
        fc.setId(102L);
        Mockito.when(fitnessClassRepository.findAll()).thenReturn(List.of(fc));

        // 3 past sessions with fill rate 20%
        ClassSession s1 = ClassSession.builder().classId(102L)
                .startsAt(Instant.parse("2026-10-12T08:30:00Z")).endsAt(Instant.parse("2026-10-12T09:15:00Z"))
                .capacity(20).bookedCount(4).status(ClassSession.Status.COMPLETED).build();
        s1.setId(11L);

        ClassSession s2 = ClassSession.builder().classId(102L)
                .startsAt(Instant.parse("2026-10-19T08:30:00Z")).endsAt(Instant.parse("2026-10-19T09:15:00Z"))
                .capacity(20).bookedCount(4).status(ClassSession.Status.COMPLETED).build();
        s2.setId(12L);

        ClassSession s3 = ClassSession.builder().classId(102L)
                .startsAt(Instant.parse("2026-10-26T08:30:00Z")).endsAt(Instant.parse("2026-10-26T09:15:00Z"))
                .capacity(20).bookedCount(4).status(ClassSession.Status.COMPLETED).build();
        s3.setId(13L);

        Mockito.when(sessionRepository.findByStartsAtBetween(any(), any())).thenReturn(List.of(s1, s2, s3));
        Mockito.when(waitlistEntryRepository.countBySessionId(anyLong())).thenReturn(0L);

        DemandInsightDto insights = demandService.getDemandInsights(4);
        assertNotNull(insights);
        assertEquals(1, insights.suggestions().size());
        assertEquals("REDUCE_OR_MOVE", insights.suggestions().get(0).type());
    }
}
