package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.entity.WaitlistEntry;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.model.WaitlistStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.booking.repository.WaitlistEntryRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.security.TokenService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WaitlistPromotionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private FitnessClass testClass;
    private User trainerUser;
    private User bookedMember;
    private String bookedMemberToken;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();

        trainerUser = userRepository.save(User.builder()
                .email("trainer.wl." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer WL")
                .dateOfBirth(LocalDate.of(1985, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));

        testClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Waitlist Class " + stamp)
                .category("PILATES")
                .durationMinutes(60)
                .intensity(ClassIntensity.MODERATE)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("4.0"))
                .minAge(14)
                .defaultCapacity(1)
                .active(true)
                .metadataReviewed(true)
                .createdBy(trainerUser.getId())
                .build());

        bookedMember = userRepository.save(User.builder()
                .email("member.booked." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Booked Member")
                .dateOfBirth(LocalDate.of(1993, 4, 15))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(bookedMember.getId(), UserRole.Role.MEMBER)));
        bookedMemberToken = tokenService.generateAccessToken(bookedMember.getId(), bookedMember.getEmail(), List.of("MEMBER"), List.of());
    }

    @Test
    @DisplayName("Waitlist promotion is FIFO: First waiting candidate is promoted")
    void testFifoPromotionOrder() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(3)))
                .endsAt(clock.instant().plus(Duration.ofDays(3)).plus(Duration.ofMinutes(60)))
                .capacity(1)
                .bookedCount(1)
                .room("Room WL-FIFO")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(70000L)
                .build());

        Booking confirmedBooking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(bookedMember.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-b1")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Member A (earlier)
        long stamp = clock.millis();
        User memberA = userRepository.save(User.builder()
                .email("memberA." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Alice")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberA.getId(), UserRole.Role.MEMBER)));

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberA.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant().minus(Duration.ofMinutes(10)))
                .build());

        // Member B (later)
        User memberB = userRepository.save(User.builder()
                .email("memberB." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Bob")
                .dateOfBirth(LocalDate.of(1996, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberB.getId(), UserRole.Role.MEMBER)));

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberB.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant().minus(Duration.ofMinutes(5)))
                .build());

        // Booked member cancels booking
        mockMvc.perform(delete("/api/v1/bookings/" + confirmedBooking.getId())
                        .header("Authorization", "Bearer " + bookedMemberToken))
                .andExpect(status().isNoContent());

        // Member A should be PROMOTED
        WaitlistEntry entryA = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberA.getId()).orElseThrow();
        assertEquals(WaitlistStatus.PROMOTED, entryA.getStatus());

        // Member B should still be WAITING
        WaitlistEntry entryB = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberB.getId()).orElseThrow();
        assertEquals(WaitlistStatus.WAITING, entryB.getStatus());

        // Booking created for member A
        assertTrue(bookingRepository.findBySessionIdAndMemberIdAndActiveFlag(session.getId(), memberA.getId(), (byte) 1).isPresent());
    }

    @Test
    @DisplayName("Waitlist promotion skips candidate without entitlement quota and promotes next candidate")
    void testPromotionSkipsCandidateWithoutEntitlement() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(4)))
                .endsAt(clock.instant().plus(Duration.ofDays(4)).plus(Duration.ofMinutes(60)))
                .capacity(1)
                .bookedCount(1)
                .room("Room WL-Quota")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(70000L)
                .build());

        Booking confirmedBooking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(bookedMember.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-b2")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Member A has already used 3 sessions (exhausted quota)
        long stamp = clock.millis();
        User memberA = userRepository.save(User.builder()
                .email("memberA.exhausted." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Alice Exhausted")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberA.getId(), UserRole.Role.MEMBER)));

        for (int i = 0; i < 3; i++) {
            bookingRepository.save(Booking.builder()
                    .sessionId(session.getId())
                    .memberId(memberA.getId())
                    .status(BookingStatus.ATTENDED)
                    .consumedFrom(ConsumedFrom.FREE_SESSION)
                    .idempotencyKey("idemp-exh-" + i)
                    .bookedAt(clock.instant())
                    .version(0)
                    .createdAt(clock.instant())
                    .build());
        }

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberA.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant().minus(Duration.ofMinutes(10)))
                .build());

        // Member B has 0 used sessions
        User memberB = userRepository.save(User.builder()
                .email("memberB.valid." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Bob Valid")
                .dateOfBirth(LocalDate.of(1996, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberB.getId(), UserRole.Role.MEMBER)));

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberB.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant().minus(Duration.ofMinutes(5)))
                .build());

        // Booked member cancels
        mockMvc.perform(delete("/api/v1/bookings/" + confirmedBooking.getId())
                        .header("Authorization", "Bearer " + bookedMemberToken))
                .andExpect(status().isNoContent());

        // Member A should be marked LEFT due to failed entitlement
        WaitlistEntry entryA = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberA.getId()).orElseThrow();
        assertEquals(WaitlistStatus.LEFT, entryA.getStatus(), "Member A must be marked LEFT");

        // Member B should be PROMOTED
        WaitlistEntry entryB = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberB.getId()).orElseThrow();
        assertEquals(WaitlistStatus.PROMOTED, entryB.getStatus(), "Member B must be PROMOTED");

        // Booking created for member B
        assertTrue(bookingRepository.findBySessionIdAndMemberIdAndActiveFlag(session.getId(), memberB.getId(), (byte) 1).isPresent());
    }

    @Test
    @DisplayName("Waitlist promotion skips candidate with schedule overlap and promotes next candidate")
    void testPromotionSkipsCandidateWithScheduleOverlap() throws Exception {
        Instant sStart = clock.instant().plus(Duration.ofDays(5));
        Instant sEnd = sStart.plus(Duration.ofMinutes(60));

        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(sStart)
                .endsAt(sEnd)
                .capacity(1)
                .bookedCount(1)
                .room("Room WL-Overlap")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(70000L)
                .build());

        Booking confirmedBooking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(bookedMember.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-b3")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Member A has an active booking during [sStart, sEnd] in another session
        long stamp = clock.millis();
        User memberA = userRepository.save(User.builder()
                .email("memberA.overlap." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Alice Overlap")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberA.getId(), UserRole.Role.MEMBER)));

        ClassSession parallelSession = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(sStart)
                .endsAt(sEnd)
                .capacity(5)
                .bookedCount(1)
                .room("Parallel Room")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(70000L)
                .build());

        bookingRepository.save(Booking.builder()
                .sessionId(parallelSession.getId())
                .memberId(memberA.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-parallel")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberA.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant().minus(Duration.ofMinutes(10)))
                .build());

        // Member B has no overlap
        User memberB = userRepository.save(User.builder()
                .email("memberB.free." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Bob Free")
                .dateOfBirth(LocalDate.of(1996, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberB.getId(), UserRole.Role.MEMBER)));

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberB.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant().minus(Duration.ofMinutes(5)))
                .build());

        // Booked member cancels
        mockMvc.perform(delete("/api/v1/bookings/" + confirmedBooking.getId())
                        .header("Authorization", "Bearer " + bookedMemberToken))
                .andExpect(status().isNoContent());

        // Member A marked LEFT due to overlap
        WaitlistEntry entryA = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberA.getId()).orElseThrow();
        assertEquals(WaitlistStatus.LEFT, entryA.getStatus(), "Member A must be marked LEFT");

        // Member B PROMOTED
        WaitlistEntry entryB = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberB.getId()).orElseThrow();
        assertEquals(WaitlistStatus.PROMOTED, entryB.getStatus(), "Member B must be PROMOTED");
    }
}
