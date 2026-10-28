package com.fitflow.membership;

import com.fitflow.booking.DefaultFreeTierEntitlementService;
import com.fitflow.booking.Entitlement;
import com.fitflow.booking.EntitlementService;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class EntitlementOrderIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntitlementService entitlementService;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private com.fitflow.membership.repository.MembershipChangeRepository membershipChangeRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.fitflow.fitnessclass.repository.FitnessClassRepository fitnessClassRepository;

    @Autowired
    private com.fitflow.session.repository.ClassSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private Clock clock;

    private User member;
    private MembershipPlan freePlan;
    private MembershipPlan basicPlan;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        membershipChangeRepository.deleteAll();
        membershipRepository.deleteAll();

        long stamp = clock.millis();

        member = userRepository.save(User.builder()
                .email("entitlement." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Entitlement Test Member")
                .dateOfBirth(LocalDate.of(1994, 4, 4))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member.getId(), UserRole.Role.MEMBER)));

        freePlan = planRepository.findByCode("FREE").orElseThrow();
        basicPlan = planRepository.findByCode("BASIC").orElseThrow();

        membershipService.assignFreeMembershipIfMissing(member.getId());
    }

    @Test
    @DisplayName("Architecture: RealEntitlementService is the active Spring bean, stub is not present")
    void testRealEntitlementServiceIsActiveBean() {
        assertTrue(entitlementService instanceof RealEntitlementService,
                "entitlementService must be an instance of RealEntitlementService");

        assertThrows(NoSuchBeanDefinitionException.class, () ->
                applicationContext.getBean(DefaultFreeTierEntitlementService.class)
        );
    }

    @Test
    @DisplayName("Entitlement Priority 1: Paid membership is consumed before FREE membership")
    void testPaidPlanConsumedBeforeFreePlan() {
        Instant now = clock.instant();

        // Create active paid BASIC membership
        Membership paidMem = membershipRepository.save(Membership.builder()
                .memberId(member.getId())
                .planId(basicPlan.getId())
                .status(MembershipStatus.ACTIVE)
                .startedAt(now)
                .endsAt(now.plus(Duration.ofDays(30)))
                .sessionsTotal(8)
                .sessionsUsed(0)
                .amountPaidPaise(49900L)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Entitlement ent = entitlementService.reserve(member.getId(), 100L, UUID.randomUUID().toString());

        assertNotNull(ent);
        assertEquals(ConsumedFrom.PLAN_SESSION, ent.consumedFrom());
        assertEquals(paidMem.getId(), ent.membershipId());

        // Verify sessionsUsed incremented on paid plan, free plan unchanged
        Membership updatedPaid = membershipRepository.findById(paidMem.getId()).orElseThrow();
        assertEquals(1, updatedPaid.getSessionsUsed());

        Membership freeMem = membershipRepository.findFirstByMemberIdAndPlanIdAndStatus(
                member.getId(), freePlan.getId(), MembershipStatus.ACTIVE
        ).orElseThrow();
        assertEquals(0, freeMem.getSessionsUsed());
    }

    @Test
    @DisplayName("Entitlement Priority 2: When paid plan is expired, FREE tier is consumed")
    void testExpiredPaidPlanFallsBackToFree() {
        Instant now = clock.instant();

        // Create expired paid plan
        membershipRepository.save(Membership.builder()
                .memberId(member.getId())
                .planId(basicPlan.getId())
                .status(MembershipStatus.ACTIVE)
                .startedAt(now.minus(Duration.ofDays(31)))
                .endsAt(now.minus(Duration.ofDays(1)))
                .sessionsTotal(8)
                .sessionsUsed(0)
                .amountPaidPaise(49900L)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Entitlement ent = entitlementService.reserve(member.getId(), 100L, UUID.randomUUID().toString());

        assertNotNull(ent);
        assertEquals(ConsumedFrom.FREE_SESSION, ent.consumedFrom());

        Membership freeMem = membershipRepository.findFirstByMemberIdAndPlanIdAndStatus(
                member.getId(), freePlan.getId(), MembershipStatus.ACTIVE
        ).orElseThrow();
        assertEquals(1, freeMem.getSessionsUsed());
    }

    @Test
    @DisplayName("Entitlement Priority 3: When all quotas are exhausted, throws 409 UPGRADE_REQUIRED with plans")
    void testQuotaExhaustionThrowsUpgradeRequired() {
        Membership freeMem = membershipRepository.findFirstByMemberIdAndPlanIdAndStatus(
                member.getId(), freePlan.getId(), MembershipStatus.ACTIVE
        ).orElseThrow();

        // Exhaust free tier
        freeMem.setSessionsUsed(3);
        membershipRepository.save(freeMem);

        ApiException ex = assertThrows(ApiException.class, () ->
                entitlementService.reserve(member.getId(), 100L, UUID.randomUUID().toString())
        );

        assertEquals("UPGRADE_REQUIRED", ex.getErrorCode().name());
        assertTrue(ex.getProperties().containsKey("plans"));
    }

    @Test
    @DisplayName("Restore: Cancelling booking restores session quota; restore runs strictly once")
    void testRestoreRestoresQuotaAndNeverRestoresTwice() {
        Instant now = clock.instant();

        var fitnessClass = fitnessClassRepository.save(com.fitflow.fitnessclass.entity.FitnessClass.builder()
                .name("Yoga Flow " + clock.millis())
                .category("YOGA")
                .durationMinutes(60)
                .intensity(com.fitflow.fitnessclass.model.ClassIntensity.LOW)
                .impactLevel(com.fitflow.fitnessclass.model.ClassImpactLevel.LOW)
                .metValue(new java.math.BigDecimal("3.0"))
                .minAge(14)
                .defaultCapacity(20)
                .active(true)
                .metadataReviewed(true)
                .createdBy(member.getId())
                .build());

        var session = sessionRepository.save(com.fitflow.session.entity.ClassSession.builder()
                .classId(fitnessClass.getId())
                .trainerId(member.getId())
                .startsAt(now.plus(Duration.ofDays(1)))
                .endsAt(now.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)))
                .capacity(20)
                .bookedCount(1)
                .room("Room A")
                .status(com.fitflow.session.entity.ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        Membership freeMem = membershipRepository.findFirstByMemberIdAndPlanIdAndStatus(
                member.getId(), freePlan.getId(), MembershipStatus.ACTIVE
        ).orElseThrow();
        freeMem.setSessionsUsed(2);
        membershipRepository.save(freeMem);

        Booking booking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(member.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .membershipId(freeMem.getId())
                .idempotencyKey(UUID.randomUUID().toString())
                .bookedAt(now)
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(now)
                .build());

        // First restore call
        entitlementService.restore(booking.getId(), "Member cancelled before cutoff");

        Booking restoredBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertNotNull(restoredBooking.getRestoredAt());

        Membership updatedFree = membershipRepository.findById(freeMem.getId()).orElseThrow();
        assertEquals(1, updatedFree.getSessionsUsed()); // Decremented from 2 to 1

        // Second restore call on the same booking
        entitlementService.restore(booking.getId(), "Duplicate cancel event");

        Membership unchangedFree = membershipRepository.findById(freeMem.getId()).orElseThrow();
        assertEquals(1, unchangedFree.getSessionsUsed()); // Still 1, NOT decremented twice!
    }
}
