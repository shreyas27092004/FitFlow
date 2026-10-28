package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.dto.AttendanceRecordDto;
import com.fitflow.booking.dto.CompleteSessionRequest;
import com.fitflow.booking.dto.MarkAttendanceRequest;
import com.fitflow.booking.entity.AttendanceRecord;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.AttendanceStatus;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.repository.AttendanceRecordRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.payout.entity.TrainerSessionEarnings;
import com.fitflow.payout.repository.TrainerSessionEarningsRepository;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AttendanceAndEarningsIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.fitflow.booking.repository.WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private TrainerSessionEarningsRepository earningsRepository;

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
    private String trainerToken;
    private User otherTrainerUser;
    private String otherTrainerToken;
    private User memberUser;
    private String memberToken;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository.deleteAll();
        earningsRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();

        trainerUser = userRepository.save(User.builder()
                .email("trainer.attend." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer John")
                .dateOfBirth(LocalDate.of(1985, 5, 5))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));
        trainerToken = tokenService.generateAccessToken(trainerUser.getId(), trainerUser.getEmail(), List.of("TRAINER"), List.of());

        otherTrainerUser = userRepository.save(User.builder()
                .email("other.trainer." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Other")
                .dateOfBirth(LocalDate.of(1987, 7, 7))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(otherTrainerUser.getId(), UserRole.Role.TRAINER)));
        otherTrainerToken = tokenService.generateAccessToken(otherTrainerUser.getId(), otherTrainerUser.getEmail(), List.of("TRAINER"), List.of());

        memberUser = userRepository.save(User.builder()
                .email("member.attend." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Mark")
                .dateOfBirth(LocalDate.of(1997, 8, 8))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of());

        testClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Cardio Rush " + stamp)
                .category("CARDIO")
                .durationMinutes(60)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(new BigDecimal("9.0"))
                .minAge(14)
                .defaultCapacity(10)
                .active(true)
                .metadataReviewed(true)
                .createdBy(trainerUser.getId())
                .build());
    }

    @Test
    @DisplayName("Trainer cannot mark attendance for another trainer's session: returns 404 NOT_FOUND")
    void testTrainerCannotMarkOtherTrainersSession() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofMinutes(30)))
                .endsAt(clock.instant().plus(Duration.ofMinutes(30)))
                .capacity(10)
                .bookedCount(1)
                .room("Room 1")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(75000L)
                .build());

        MarkAttendanceRequest req = new MarkAttendanceRequest(List.of(new AttendanceRecordDto(1L, AttendanceStatus.ATTENDED)));
        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/attendance")
                        .header("Authorization", "Bearer " + otherTrainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("Marking attendance before session starts returns 409 BOOKING_RULE_VIOLATION")
    void testAttendanceBeforeStartFails() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().plus(Duration.ofHours(2)))
                .endsAt(clock.instant().plus(Duration.ofHours(3)))
                .capacity(10)
                .bookedCount(1)
                .room("Room 2")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(75000L)
                .build());

        MarkAttendanceRequest req = new MarkAttendanceRequest(List.of(new AttendanceRecordDto(1L, AttendanceStatus.ATTENDED)));
        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/attendance")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")))
                .andExpect(jsonPath("$.detail", containsString("cannot be marked before the session has started")));
    }

    @Test
    @DisplayName("Marking attendance > 24 hours after session end returns 409 BOOKING_RULE_VIOLATION")
    void testAttendanceAfter24HoursFails() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofHours(30)))
                .endsAt(clock.instant().minus(Duration.ofHours(29)))
                .capacity(10)
                .bookedCount(1)
                .room("Room 3")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(75000L)
                .build());

        MarkAttendanceRequest req = new MarkAttendanceRequest(List.of(new AttendanceRecordDto(1L, AttendanceStatus.ATTENDED)));
        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/attendance")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")))
                .andExpect(jsonPath("$.detail", containsString("cutoff is 24 hours after session end")));
    }

    @Test
    @DisplayName("Valid attendance marking updates bookings, creates attendance records, and emits outbox event")
    void testValidAttendanceMarking() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofMinutes(45)))
                .endsAt(clock.instant().plus(Duration.ofMinutes(15)))
                .capacity(10)
                .bookedCount(2)
                .room("Room 4")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(75000L)
                .build());

        Booking b1 = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-att-1")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        User member2 = userRepository.save(User.builder()
                .email("m2.att." + clock.millis() + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Two")
                .dateOfBirth(LocalDate.of(1996, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member2.getId(), UserRole.Role.MEMBER)));

        Booking b2 = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(member2.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-att-2")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        MarkAttendanceRequest req = new MarkAttendanceRequest(List.of(
                new AttendanceRecordDto(b1.getId(), AttendanceStatus.ATTENDED),
                new AttendanceRecordDto(b2.getId(), AttendanceStatus.NO_SHOW)
        ));

        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/attendance")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is(session.getId().intValue())))
                .andExpect(jsonPath("$.markedCount", is(2)));

        assertEquals(BookingStatus.ATTENDED, bookingRepository.findById(b1.getId()).orElseThrow().getStatus());
        assertEquals(BookingStatus.NO_SHOW, bookingRepository.findById(b2.getId()).orElseThrow().getStatus());

        Optional<AttendanceRecord> rec1 = attendanceRecordRepository.findByBookingId(b1.getId());
        assertTrue(rec1.isPresent());
        assertEquals(AttendanceStatus.ATTENDED, rec1.get().getStatus());

        Optional<AttendanceRecord> rec2 = attendanceRecordRepository.findByBookingId(b2.getId());
        assertTrue(rec2.isPresent());
        assertEquals(AttendanceStatus.NO_SHOW, rec2.get().getStatus());

        boolean outboxHasAttendance = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "attendance.marked".equals(e.getEventType()));
        assertTrue(outboxHasAttendance, "attendance.marked outbox event must be emitted");
    }

    @Test
    @DisplayName("Complete session before endsAt returns 409 BOOKING_RULE_VIOLATION")
    void testCompleteSessionBeforeEndFails() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofMinutes(30)))
                .endsAt(clock.instant().plus(Duration.ofMinutes(30))) // not ended yet!
                .capacity(10)
                .bookedCount(0)
                .room("Room 5")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(75000L)
                .build());

        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/complete")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteSessionRequest(true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")))
                .andExpect(jsonPath("$.detail", containsString("cannot be completed before it has ended")));
    }

    @Test
    @DisplayName("Complete session without attendees and without noAttendees=true returns 409 BOOKING_RULE_VIOLATION")
    void testCompleteSessionWithoutAttendeesFails() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofHours(2)))
                .endsAt(clock.instant().minus(Duration.ofHours(1))) // ended!
                .capacity(10)
                .bookedCount(0)
                .room("Room 6")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(75000L)
                .build());

        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/complete")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteSessionRequest(false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")))
                .andExpect(jsonPath("$.detail", containsString("requires at least one attendance record or explicit noAttendees=true")));
    }

    @Test
    @DisplayName("Complete session with attendance creates exactly ONE earnings row and emits session.completed")
    void testCompleteSessionSuccessAndDuplicateBlocked() throws Exception {
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(clock.instant().minus(Duration.ofHours(2)))
                .endsAt(clock.instant().minus(Duration.ofHours(1)))
                .capacity(10)
                .bookedCount(1)
                .room("Room 7")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(85000L)
                .build());

        Booking booking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(memberUser.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-comp")
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        attendanceRecordRepository.save(AttendanceRecord.builder()
                .bookingId(booking.getId())
                .markedBy(trainerUser.getId())
                .markedAt(clock.instant().minus(Duration.ofMinutes(90)))
                .status(AttendanceStatus.ATTENDED)
                .createdAt(clock.instant().minus(Duration.ofMinutes(90)))
                .build());

        // First complete call -> 200 OK
        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/complete")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteSessionRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.earningsPaise", is(85000)));

        ClassSession refreshed = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(ClassSession.Status.COMPLETED, refreshed.getStatus());

        List<TrainerSessionEarnings> earnings = earningsRepository.findAll();
        assertEquals(1, earnings.size(), "Exactly ONE earnings row must be created");
        assertEquals(85000L, earnings.get(0).getAmountPaise());
        assertEquals(trainerUser.getId(), earnings.get(0).getTrainerId());

        boolean sessionCompletedEmitted = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "session.completed".equals(e.getEventType()));
        assertTrue(sessionCompletedEmitted, "session.completed outbox event must be emitted");

        // Second complete call on already completed session -> 409 Conflict
        mockMvc.perform(post("/api/v1/trainer/sessions/" + session.getId() + "/complete")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteSessionRequest(false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("BOOKING_RULE_VIOLATION")));

        assertEquals(1, earningsRepository.findAll().size(), "Earnings row count must remain 1 (no duplicate)");
    }
}
