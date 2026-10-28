package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.TokenService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AtomicityRollbackIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.fitflow.booking.repository.WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private FitnessClass testClass;
    private User trainerUser;
    private User memberUser;
    private String memberToken;

    @BeforeEach
    void setUp() {
        BookingService.setAtomicityFailureSimulation(false);
        outboxEventRepository.deleteAll();
        waitlistEntryRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();

        trainerUser = userRepository.save(User.builder()
                .email("trainer.atom." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Atom")
                .dateOfBirth(LocalDate.of(1989, 3, 3))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));

        memberUser = userRepository.save(User.builder()
                .email("member.atom." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Atom")
                .dateOfBirth(LocalDate.of(1998, 7, 7))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of());

        testClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Atomicity Strength " + stamp)
                .category("STRENGTH")
                .durationMinutes(60)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(new BigDecimal("7.5"))
                .minAge(16)
                .defaultCapacity(10)
                .active(true)
                .metadataReviewed(true)
                .createdBy(trainerUser.getId())
                .build());
    }

    @AfterEach
    void tearDown() {
        BookingService.setAtomicityFailureSimulation(false);
    }

    @Test
    @DisplayName("Atomicity: Forced exception after booking insert rolls back booked_count, booking row, outbox row, and quota")
    void testAtomicityRollbackOnFailureAfterInsert() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(2)))
                .endsAt(clock.instant().plus(Duration.ofDays(2)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(0)
                .room("Room Atomicity")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(80000L)
                .build());

        // Enable simulated failure hook
        BookingService.setAtomicityFailureSimulation(true);

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-atomicity-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());

        // Verify Rollback
        // 1. Session booked_count must remain 0
        ClassSession refreshedSession = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(0, refreshedSession.getBookedCount(), "booked_count must be rolled back to 0");

        // 2. Booking row must not exist in DB
        List<Booking> bookings = bookingRepository.findBySessionId(session.getId());
        assertTrue(bookings.isEmpty(), "Booking row must be rolled back");

        // 3. Outbox event must not exist in DB
        boolean outboxContainsBooking = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "booking.created".equals(e.getEventType()));
        assertTrue(!outboxContainsBooking, "Outbox event must be rolled back");

        // 4. Entitlement quota must not be consumed
        long usedQuota = bookingRepository.countByMemberIdAndStatusIn(
                memberUser.getId(),
                Set.of(BookingStatus.CONFIRMED, BookingStatus.ATTENDED, BookingStatus.NO_SHOW)
        );
        assertEquals(0, usedQuota, "Entitlement quota must remain 0");
    }
}
