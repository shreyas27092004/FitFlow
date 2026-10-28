package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.dto.AdminCancelBookingRequest;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.entity.WaitlistEntry;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.model.WaitlistStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.booking.repository.IdempotencyRecordRepository;
import com.fitflow.booking.repository.WaitlistEntryRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingRulesMatrixIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

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

    private FitnessClass defaultClass;
    private FitnessClass adultOnlyClass;
    private User trainerUser;
    private User memberUser;
    private String memberToken;
    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository.deleteAll();
        bookingRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        sessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();

        trainerUser = userRepository.save(User.builder()
                .email("trainer.rules." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Dave")
                .dateOfBirth(LocalDate.of(1988, 6, 12))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));

        memberUser = userRepository.save(User.builder()
                .email("member.rules." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Alice Member")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of());

        adminUser = userRepository.save(User.builder()
                .email("admin.rules." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Admin Frank")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(adminUser.getId(), UserRole.Role.ADMIN)));
        adminToken = tokenService.generateAccessToken(adminUser.getId(), adminUser.getEmail(), List.of("ADMIN"), List.of("USER_EDIT", "SUPER_ADMIN"));

        defaultClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Yoga Flow " + stamp)
                .category("YOGA")
                .durationMinutes(60)
                .intensity(ClassIntensity.LOW)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("3.5"))
                .minAge(14)
                .defaultCapacity(10)
                .active(true)
                .metadataReviewed(true)
                .createdBy(adminUser.getId())
                .build());

        adultOnlyClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Heavy Boxing " + stamp)
                .category("BOXING")
                .durationMinutes(60)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(new BigDecimal("12.0"))
                .minAge(18)
                .defaultCapacity(10)
                .active(true)
                .metadataReviewed(true)
                .createdBy(adminUser.getId())
                .build());
    }

    // -------------------------------------------------------------------------
    // Rule 1 Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 1: Session not found returns 404 NOT_FOUND")
    void testRule1_sessionNotFound() throws Exception {
        CreateBookingRequest req = new CreateBookingRequest(999999L);
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-notfound-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("Rule 1: Session CANCELLED returns 409 BOOKING_RULE_VIOLATION")
    void testRule1_sessionCancelled() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(2)))
                .endsAt(clock.instant().plus(Duration.ofDays(2)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(0)
                .room("Room A")
                .status(ClassSession.Status.CANCELLED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-cancelled-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")));
    }

    @Test
    @DisplayName("Rule 1: Session starting in past returns 409 BOOKING_RULE_VIOLATION")
    void testRule1_sessionInPast() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofHours(2)))
                .endsAt(clock.instant().minus(Duration.ofHours(1)))
                .capacity(10)
                .bookedCount(0)
                .room("Room Past")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-past-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")));
    }

    // -------------------------------------------------------------------------
    // Rule 2 Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 2: Member age < class.minAge returns 403 AGE_RESTRICTED")
    void testRule2_ageRestrictedUnderage() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(adultOnlyClass.getId()) // minAge 18
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(2)))
                .endsAt(clock.instant().plus(Duration.ofDays(2)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(0)
                .room("Room Adult")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        // 16-year-old member
        User teenUser = userRepository.save(User.builder()
                .email("teen." + clock.millis() + "@test.com")
                .passwordHash("hashed")
                .fullName("Teen Member")
                .dateOfBirth(LocalDate.of(2010, 5, 10))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(teenUser.getId(), UserRole.Role.MEMBER)));
        String teenToken = tokenService.generateAccessToken(teenUser.getId(), teenUser.getEmail(), List.of("MEMBER"), List.of());

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + teenToken)
                        .header("Idempotency-Key", "key-teen-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AGE_RESTRICTED")));
    }

    @Test
    @DisplayName("Rule 2: Member with no DOB for minAge > 14 returns 403 AGE_RESTRICTED with profile message")
    void testRule2_missingDobForAgeGatedClass() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(adultOnlyClass.getId()) // minAge 18
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(2)))
                .endsAt(clock.instant().plus(Duration.ofDays(2)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(0)
                .room("Room Adult")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        // Member without DOB
        User noDobUser = userRepository.save(User.builder()
                .email("nodob." + clock.millis() + "@test.com")
                .passwordHash("hashed")
                .fullName("No DOB Member")
                .dateOfBirth(null)
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(noDobUser.getId(), UserRole.Role.MEMBER)));
        String noDobToken = tokenService.generateAccessToken(noDobUser.getId(), noDobUser.getEmail(), List.of("MEMBER"), List.of());

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + noDobToken)
                        .header("Idempotency-Key", "key-nodob-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AGE_RESTRICTED")))
                .andExpect(jsonPath("$.detail", containsString("Please add your date of birth in your profile")));
    }

    // -------------------------------------------------------------------------
    // Rule 3 Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 3: Overlapping confirmed booking returns 409 BOOKING_RULE_VIOLATION")
    void testRule3_overlappingBookingConflict() throws Exception {
        Instant tStart = clock.instant().plus(Duration.ofDays(3));
        Instant tEnd = tStart.plus(Duration.ofMinutes(60));

        ClassSession session1 = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(tStart)
                .endsAt(tEnd)
                .capacity(10)
                .bookedCount(1)
                .room("Room A")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        // Create confirmed booking for session1
        bookingRepository.save(Booking.builder()
                .sessionId(session1.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-prev")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Session 2 overlaps session 1 (starts 30 mins into session 1)
        ClassSession session2 = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(tStart.plus(Duration.ofMinutes(30)))
                .endsAt(tEnd.plus(Duration.ofMinutes(30)))
                .capacity(10)
                .bookedCount(0)
                .room("Room B")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(session2.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-overlap-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")))
                .andExpect(jsonPath("$.detail", containsString("already has an active confirmed booking during this session's time window")));
    }

    // -------------------------------------------------------------------------
    // Rule 4 Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 4: Exceeded free tier quota returns 409 UPGRADE_REQUIRED")
    void testRule4_quotaExceededUpgradeRequired() throws Exception {
        // Create 3 existing confirmed bookings for memberUser (reaching the default stub-free-sessions=3 limit)
        for (int i = 0; i < 3; i++) {
            ClassSession dummySession = sessionRepository.save(ClassSession.builder()
                    .classId(defaultClass.getId())
                    .trainerId(trainerUser.getId())
                    .startsAt(clock.instant().plus(Duration.ofDays(10 + i)))
                    .endsAt(clock.instant().plus(Duration.ofDays(10 + i)).plus(Duration.ofMinutes(60)))
                    .capacity(10)
                    .bookedCount(1)
                    .room("Room D" + i)
                    .status(ClassSession.Status.SCHEDULED)
                    .trainerRateSnapshotPaise(50000L)
                    .build());

            bookingRepository.save(Booking.builder()
                    .sessionId(dummySession.getId())
                    .memberId(memberUser.getId())
                    .status(BookingStatus.CONFIRMED)
                    .consumedFrom(ConsumedFrom.FREE_SESSION)
                    .idempotencyKey("idemp-quota-" + i)
                    .bookedAt(clock.instant())
                    .activeFlag((byte) 1)
                    .version(0)
                    .createdAt(clock.instant())
                    .build());
        }

        // Attempt to book 4th session
        ClassSession targetSession = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(20)))
                .endsAt(clock.instant().plus(Duration.ofDays(20)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(0)
                .room("Room Target")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(targetSession.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-quota-exceeded-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("UPGRADE_REQUIRED")))
                .andExpect(jsonPath("$.detail", containsString("Upgrade required")));
    }

    @Test
    @DisplayName("Cancel and rebook: Cancelling booking restores free session entitlement allowing rebooking")
    void testCancelAndRebookRestoresEntitlement() throws Exception {
        // Create 2 existing bookings
        for (int i = 0; i < 2; i++) {
            ClassSession s = sessionRepository.save(ClassSession.builder()
                    .classId(defaultClass.getId())
                    .trainerId(trainerUser.getId())
                    .startsAt(clock.instant().plus(Duration.ofDays(10 + i)))
                    .endsAt(clock.instant().plus(Duration.ofDays(10 + i)).plus(Duration.ofMinutes(60)))
                    .capacity(10)
                    .bookedCount(1)
                    .room("Room S" + i)
                    .status(ClassSession.Status.SCHEDULED)
                    .trainerRateSnapshotPaise(50000L)
                    .build());

            bookingRepository.save(Booking.builder()
                    .sessionId(s.getId())
                    .memberId(memberUser.getId())
                    .status(BookingStatus.CONFIRMED)
                    .consumedFrom(ConsumedFrom.FREE_SESSION)
                    .idempotencyKey("idemp-cnr-" + i)
                    .bookedAt(clock.instant())
                    .activeFlag((byte) 1)
                    .version(0)
                    .createdAt(clock.instant())
                    .build());
        }

        // 3rd booking to reach limit
        ClassSession session3 = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(15)))
                .endsAt(clock.instant().plus(Duration.ofDays(15)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(1)
                .room("Room S3")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        Booking booking3 = bookingRepository.save(Booking.builder()
                .sessionId(session3.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-cnr-3")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Cancel booking3 before cutoff (starts in 15 days, cutoff is 4 hours before)
        mockMvc.perform(delete("/api/v1/bookings/" + booking3.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());

        // Now book session4 -> should succeed with 201 because entitlement was restored!
        ClassSession session4 = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(20)))
                .endsAt(clock.instant().plus(Duration.ofDays(20)).plus(Duration.ofMinutes(60)))
                .capacity(10)
                .bookedCount(0)
                .room("Room S4")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(session4.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-cnr-rebook-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    // -------------------------------------------------------------------------
    // Rule 5 Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 5: Normal seat allocation returns 201 CONFIRMED and writes outbox row")
    void testRule5_seatAllocationSuccess() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(2)))
                .endsAt(clock.instant().plus(Duration.ofDays(2)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(0)
                .room("Room Success")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(60000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-success-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.bookingId", notNullValue()))
                .andExpect(jsonPath("$.sessionId", is(session.getId().intValue())));

        ClassSession refreshed = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(1, refreshed.getBookedCount());

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        boolean foundBookingCreated = outboxList.stream()
                .anyMatch(e -> "booking.created".equals(e.getEventType()));
        assertTrue(foundBookingCreated, "booking.created event must be recorded in outbox");
    }

    @Test
    @DisplayName("Rule 5: Full session returns 202 WAITLISTED with position")
    void testRule5_sessionFullReturnsWaitlisted() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(4)))
                .endsAt(clock.instant().plus(Duration.ofDays(4)).plus(Duration.ofMinutes(60)))
                .capacity(1)
                .bookedCount(1) // Full!
                .room("Room Full")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(60000L)
                .build());

        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", "key-full-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("WAITLISTED")))
                .andExpect(jsonPath("$.position", is(1)));

        List<WaitlistEntry> waitlist = waitlistEntryRepository.findBySessionIdAndStatusOrderByCreatedAtAscIdAsc(session.getId(), WaitlistStatus.WAITING);
        assertEquals(1, waitlist.size());
        assertEquals(memberUser.getId(), waitlist.get(0).getMemberId());
    }

    // -------------------------------------------------------------------------
    // Idempotency Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Idempotency: Replaying same key and same body returns identical cached 201 response")
    void testIdempotencyReplayIdentical() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(5)))
                .endsAt(clock.instant().plus(Duration.ofDays(5)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(0)
                .room("Room Idemp")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(60000L)
                .build());

        String key = "key-replay-identical";
        CreateBookingRequest req = new CreateBookingRequest(session.getId());
        String body = objectMapper.writeValueAsString(req);

        // First call -> 201
        String firstResp = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Second call with identical key and body -> cached 201
        String secondResp = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(
                objectMapper.readTree(firstResp),
                objectMapper.readTree(secondResp)
        );
        ClassSession refreshed = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(1, refreshed.getBookedCount(), "booked_count must not increment on replay");
    }

    @Test
    @DisplayName("Idempotency: Replaying same key with different body returns 422 IDEMPOTENCY_REPLAY_MISMATCH")
    void testIdempotencyReplayMismatchReturns422() throws Exception {
        ClassSession session1 = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(5)))
                .endsAt(clock.instant().plus(Duration.ofDays(5)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(0)
                .room("Room Idemp1")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(60000L)
                .build());

        ClassSession session2 = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(6)))
                .endsAt(clock.instant().plus(Duration.ofDays(6)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(0)
                .room("Room Idemp2")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(60000L)
                .build());

        String key = "key-replay-mismatch";

        // First call with session1
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(session1.getId()))))
                .andExpect(status().isCreated());

        // Second call with same key but session2 -> 422
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + memberToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(session2.getId()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_REPLAY_MISMATCH")));
    }

    // -------------------------------------------------------------------------
    // Cancellation Cutoff Boundary Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cancellation: 1s before cutoff allows member cancel; 1s after cutoff rejects with 409")
    void testCancellationCutoffBoundaries() throws Exception {
        // Cutoff is 4 hours before session start.
        // Session A starts in 4 hours + 1 minute (so now is BEFORE cutoff)
        ClassSession sessionBeforeCutoff = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofHours(4)).plus(Duration.ofSeconds(60)))
                .endsAt(clock.instant().plus(Duration.ofHours(5)).plus(Duration.ofSeconds(60)))
                .capacity(5)
                .bookedCount(1)
                .room("Room Cutoff1")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        Booking bookingBeforeCutoff = bookingRepository.save(Booking.builder()
                .sessionId(sessionBeforeCutoff.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-cutoff-1")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Cancel before cutoff -> 204
        mockMvc.perform(delete("/api/v1/bookings/" + bookingBeforeCutoff.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());

        Booking refreshedBefore = bookingRepository.findById(bookingBeforeCutoff.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, refreshedBefore.getStatus());

        // Session B starts in 3 hours and 59 minutes (so now is AFTER 4-hour cutoff)
        ClassSession sessionAfterCutoff = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofHours(3)).plus(Duration.ofMinutes(59)))
                .endsAt(clock.instant().plus(Duration.ofHours(4)).plus(Duration.ofMinutes(59)))
                .capacity(5)
                .bookedCount(1)
                .room("Room Cutoff2")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        Booking bookingAfterCutoff = bookingRepository.save(Booking.builder()
                .sessionId(sessionAfterCutoff.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-cutoff-2")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Member cancel after cutoff -> 409 BOOKING_RULE_VIOLATION
        mockMvc.perform(delete("/api/v1/bookings/" + bookingAfterCutoff.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")))
                .andExpect(jsonPath("$.detail", containsString("Cancellation deadline has passed")));

        // Admin cancel after cutoff -> 200 OK
        AdminCancelBookingRequest adminReq = new AdminCancelBookingRequest("Medical emergency override");
        mockMvc.perform(post("/api/v1/admin/bookings/" + bookingAfterCutoff.getId() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        Booking refreshedAfter = bookingRepository.findById(bookingAfterCutoff.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, refreshedAfter.getStatus());
    }

    // -------------------------------------------------------------------------
    // Leave Waitlist & Member Bookings
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Leave waitlist: DELETE /api/v1/waitlist/{sessionId} sets status LEFT")
    void testLeaveWaitlist() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(7)))
                .endsAt(clock.instant().plus(Duration.ofDays(7)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(5)
                .room("Room W")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        waitlistEntryRepository.save(WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(memberUser.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant())
                .build());

        mockMvc.perform(delete("/api/v1/waitlist/" + session.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());

        WaitlistEntry refreshed = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), memberUser.getId()).orElseThrow();
        assertEquals(WaitlistStatus.LEFT, refreshed.getStatus());
    }

    @Test
    @DisplayName("My Bookings: GET /api/v1/me/bookings returns paginated list with class details")
    void testGetMyBookings() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(3)))
                .endsAt(clock.instant().plus(Duration.ofDays(3)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(1)
                .room("Room Me")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-me")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        mockMvc.perform(get("/api/v1/me/bookings")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", notNullValue()))
                .andExpect(jsonPath("$.items[0].status", is("CONFIRMED")))
                .andExpect(jsonPath("$.items[0].className", containsString("Yoga Flow")));
    }

    @Test
    @DisplayName("Member cannot cancel another member's booking: returns 404 NOT_FOUND (ID leakage protection)")
    void testMemberCannotCancelOtherMemberBooking() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(defaultClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofDays(5)))
                .endsAt(clock.instant().plus(Duration.ofDays(5)).plus(Duration.ofMinutes(60)))
                .capacity(5)
                .bookedCount(1)
                .room("Room Other")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        User otherMember = userRepository.save(User.builder()
                .email("other." + clock.millis() + "@test.com")
                .passwordHash("hashed")
                .fullName("Other Member")
                .dateOfBirth(LocalDate.of(1996, 4, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(otherMember.getId(), UserRole.Role.MEMBER)));

        Booking otherBooking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(otherMember.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-other")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // memberUser attempts to delete otherBooking
        mockMvc.perform(delete("/api/v1/bookings/" + otherBooking.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }
}
