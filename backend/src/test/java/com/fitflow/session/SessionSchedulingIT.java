package com.fitflow.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.security.TokenService;
import com.fitflow.session.dto.CancelSessionRequest;
import com.fitflow.session.dto.CreateSessionRequest;
import com.fitflow.session.dto.RecurrenceRuleDto;
import com.fitflow.session.dto.RescheduleSessionRequest;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SessionSchedulingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TrainerProfileRepository trainerProfileRepository;

    @Autowired
    private com.fitflow.booking.repository.WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private com.fitflow.booking.repository.AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private com.fitflow.booking.repository.BookingRepository bookingRepository;

    @Autowired
    private com.fitflow.payout.repository.TrainerSessionEarningsRepository earningsRepository;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private String adminToken;
    private String trainerToken;
    private String memberToken;

    private User adminUser;
    private User trainerUser;
    private User memberUser;
    private FitnessClass yogaClass;

    private final ZoneId zoneId = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        waitlistEntryRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        bookingRepository.deleteAll();
        earningsRepository.deleteAll();
        sessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();

        // 1. Admin
        adminUser = userRepository.save(User.builder()
                .email("admin" + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Admin User")
                .dateOfBirth(LocalDate.of(1985, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(adminUser.getId(), UserRole.Role.ADMIN)));
        adminToken = tokenService.generateAccessToken(adminUser.getId(), adminUser.getEmail(), List.of("ADMIN"), List.of("CLASS_EDIT", "SUPER_ADMIN", "USER_READ"));

        // 2. Trainer
        trainerUser = userRepository.save(User.builder()
                .email("trainer" + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Bob")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));
        trainerToken = tokenService.generateAccessToken(trainerUser.getId(), trainerUser.getEmail(), List.of("TRAINER"), List.of());

        // Trainer available on Monday(1) and Wednesday(3) from 08:00 to 18:00
        String availabilityJson = """
                [
                  {"dayOfWeek": 1, "start": "08:00", "end": "18:00"},
                  {"dayOfWeek": 3, "start": "08:00", "end": "18:00"},
                  {"dayOfWeek": 5, "start": "08:00", "end": "18:00"}
                ]
                """;
        trainerProfileRepository.save(TrainerProfile.builder()
                .userId(trainerUser.getId())
                .ratePerSessionPaise(85000L) // 850 INR
                .availability(availabilityJson)
                .active(true)
                .build());

        // 3. Member
        memberUser = userRepository.save(User.builder()
                .email("member" + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Alice Member")
                .dateOfBirth(LocalDate.of(1998, 3, 15))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of());

        // 4. Fitness Class
        yogaClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Morning Vinyasa")
                .category("YOGA")
                .durationMinutes(60)
                .intensity(ClassIntensity.MODERATE)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("4.0"))
                .minAge(14)
                .defaultCapacity(20)
                .active(true)
                .metadataReviewed(true)
                .createdBy(adminUser.getId())
                .build());
    }

    @Test
    @DisplayName("Schedule single session: validates future, availability, snapshots rate")
    void testScheduleSingleSessionSuccess() throws Exception {
        // Next Monday at 10:00 AM Kolkata time
        ZonedDateTime nextMonday = getNextWeekdayInZone(1, 10, 0);

        CreateSessionRequest req = new CreateSessionRequest(
                yogaClass.getId(),
                trainerUser.getId(),
                nextMonday.toInstant(),
                15,
                "Studio A",
                null
        );

        mockMvc.perform(post("/api/v1/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].className", is("Morning Vinyasa")))
                .andExpect(jsonPath("$[0].capacity", is(15)))
                .andExpect(jsonPath("$[0].trainerRateSnapshotPaise", is(85000)))
                .andExpect(jsonPath("$[0].room", is("Studio A")))
                .andExpect(jsonPath("$[0].seatsLeft", is(15)));
    }

    @Test
    @DisplayName("Schedule session fails if trainer is unavailable per availability JSON")
    void testScheduleSessionTrainerUnavailable() throws Exception {
        // Tuesday (2) - trainer is NOT available on Tuesday (only 1, 3, 5)
        ZonedDateTime nextTuesday = getNextWeekdayInZone(2, 10, 0);

        CreateSessionRequest req = new CreateSessionRequest(
                yogaClass.getId(),
                trainerUser.getId(),
                nextTuesday.toInstant(),
                20,
                "Studio A",
                null
        );

        mockMvc.perform(post("/api/v1/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("conflicts detected")));
    }

    @Test
    @DisplayName("Schedule session fails if room is double-booked")
    void testScheduleSessionRoomOverlapConflict() throws Exception {
        ZonedDateTime nextMonday = getNextWeekdayInZone(1, 10, 0);

        // Pre-existing session in Studio A from 10:00 to 11:00 with another trainer
        sessionRepository.save(ClassSession.builder()
                .classId(yogaClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(nextMonday.toInstant())
                .endsAt(nextMonday.toInstant().plus(Duration.ofMinutes(60)))
                .capacity(20)
                .room("Studio A")
                .trainerRateSnapshotPaise(85000L)
                .build());

        // Try to schedule again in Studio A at 10:30 (overlaps)
        CreateSessionRequest req = new CreateSessionRequest(
                yogaClass.getId(),
                trainerUser.getId(),
                nextMonday.toInstant().plus(Duration.ofMinutes(30)),
                20,
                "Studio A",
                null
        );

        mockMvc.perform(post("/api/v1/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("conflicts detected")));
    }

    @Test
    @DisplayName("Recurrence generation: creates sessions sharing series_id across weeks and month boundary")
    void testRecurrenceAcrossWeeksAndMonthBoundary() throws Exception {
        ZonedDateTime startMonday = getNextWeekdayInZone(1, 9, 0);
        // Recurrence on Monday(1) and Wednesday(3) until 4 weeks later (crossing month boundary)
        LocalDate untilDate = startMonday.toLocalDate().plusWeeks(4);

        CreateSessionRequest req = new CreateSessionRequest(
                yogaClass.getId(),
                trainerUser.getId(),
                startMonday.toInstant(),
                20,
                "Studio B",
                new RecurrenceRuleDto(List.of(1, 3), untilDate)
        );

        mockMvc.perform(post("/api/v1/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$[0].seriesId", notNullValue()));

        List<ClassSession> allSessions = sessionRepository.findAll();
        assertFalse(allSessions.isEmpty());
        String seriesId = allSessions.get(0).getSeriesId();
        assertNotNull(seriesId);
        // All occurrences share the same series_id
        for (ClassSession s : allSessions) {
            assertEquals(seriesId, s.getSeriesId());
        }
    }

    @Test
    @DisplayName("Recurrence rejection: if any date conflicts, rejects entire batch with 409 listing dates")
    void testRecurrenceConflictRejection() throws Exception {
        ZonedDateTime startMonday = getNextWeekdayInZone(1, 9, 0);
        LocalDate untilDate = startMonday.toLocalDate().plusWeeks(2);

        // Pre-book one occurrence date (e.g. Wednesday at 9:00 AM)
        ZonedDateTime conflictWednesday = startMonday.plusDays(2);
        sessionRepository.save(ClassSession.builder()
                .classId(yogaClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(conflictWednesday.toInstant())
                .endsAt(conflictWednesday.toInstant().plus(Duration.ofMinutes(60)))
                .capacity(20)
                .room("Studio C")
                .trainerRateSnapshotPaise(85000L)
                .build());

        CreateSessionRequest req = new CreateSessionRequest(
                yogaClass.getId(),
                trainerUser.getId(),
                startMonday.toInstant(),
                20,
                "Studio C",
                new RecurrenceRuleDto(List.of(1, 3), untilDate)
        );

        mockMvc.perform(post("/api/v1/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString(conflictWednesday.toLocalDate().toString())));
    }

    @Test
    @DisplayName("Reschedule session: updates time, validates conflicts, emits session.rescheduled outbox event")
    void testRescheduleSessionSuccess() throws Exception {
        ZonedDateTime startMonday = getNextWeekdayInZone(1, 9, 0);
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(yogaClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(startMonday.toInstant())
                .endsAt(startMonday.toInstant().plus(Duration.ofMinutes(60)))
                .capacity(20)
                .room("Studio A")
                .trainerRateSnapshotPaise(85000L)
                .build());

        ZonedDateTime newTime = startMonday.plusHours(2); // 11:00 AM Monday (trainer available)
        RescheduleSessionRequest req = new RescheduleSessionRequest(newTime.toInstant(), session.getVersion());

        mockMvc.perform(patch("/api/v1/admin/sessions/" + session.getId() + "/reschedule")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt", is(newTime.toInstant().toString())));

        List<OutboxEvent> events = outboxEventRepository.findByEventType("session.rescheduled");
        assertFalse(events.isEmpty());
        OutboxEvent event = events.get(events.size() - 1);
        assertEquals(session.getId(), event.getAggregateId());
    }

    @Test
    @DisplayName("Cancel session: marks CANCELLED, emits session.cancelled outbox event")
    void testCancelSessionSuccess() throws Exception {
        ZonedDateTime startMonday = getNextWeekdayInZone(1, 9, 0);
        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(yogaClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(startMonday.toInstant())
                .endsAt(startMonday.toInstant().plus(Duration.ofMinutes(60)))
                .capacity(20)
                .room("Studio A")
                .trainerRateSnapshotPaise(85000L)
                .build());

        CancelSessionRequest req = new CancelSessionRequest("Facility emergency repair");

        mockMvc.perform(post("/api/v1/admin/sessions/" + session.getId() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        List<OutboxEvent> events = outboxEventRepository.findByEventType("session.cancelled");
        assertFalse(events.isEmpty());
        OutboxEvent event = events.get(events.size() - 1);
        assertEquals(session.getId(), event.getAggregateId());
    }

    @Test
    @DisplayName("GET /api/v1/sessions: rejects range > 62 days with 400")
    void testSessionsDateRangeExceeded() throws Exception {
        Instant now = clock.instant();
        Instant farFuture = now.plus(Duration.ofDays(70));

        mockMvc.perform(get("/api/v1/sessions")
                        .header("Authorization", "Bearer " + memberToken)
                        .param("from", now.toString())
                        .param("to", farFuture.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/reports/utilization returns class and weekly breakdowns")
    void testUtilizationReport() throws Exception {
        ZonedDateTime startMonday = getNextWeekdayInZone(1, 10, 0);
        sessionRepository.save(ClassSession.builder()
                .classId(yogaClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(startMonday.toInstant())
                .endsAt(startMonday.toInstant().plus(Duration.ofMinutes(60)))
                .capacity(20)
                .bookedCount(15)
                .room("Studio A")
                .trainerRateSnapshotPaise(85000L)
                .build());

        mockMvc.perform(get("/api/v1/admin/reports/utilization")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", startMonday.toInstant().minus(Duration.ofDays(1)).toString())
                        .param("to", startMonday.toInstant().plus(Duration.ofDays(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classUtilization", hasSize(1)))
                .andExpect(jsonPath("$.classUtilization[0].className", is("Morning Vinyasa")))
                .andExpect(jsonPath("$.classUtilization[0].totalSessions", is(1)))
                .andExpect(jsonPath("$.classUtilization[0].totalCapacity", is(20)))
                .andExpect(jsonPath("$.classUtilization[0].totalBooked", is(15)))
                .andExpect(jsonPath("$.classUtilization[0].utilizationPercent", is(75.0)))
                .andExpect(jsonPath("$.weeklyUtilization", hasSize(1)));
    }

    private ZonedDateTime getNextWeekdayInZone(int targetDayOfWeek, int hour, int minute) {
        ZonedDateTime nowZdt = clock.instant().atZone(zoneId);
        ZonedDateTime candidate = nowZdt.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!candidate.isAfter(nowZdt)) {
            candidate = candidate.plusDays(1);
        }
        while (candidate.getDayOfWeek().getValue() != targetDayOfWeek) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }
}
