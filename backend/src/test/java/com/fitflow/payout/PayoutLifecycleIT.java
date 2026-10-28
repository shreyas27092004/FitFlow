package com.fitflow.payout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.payout.dto.CreatePayoutBatchRequest;
import com.fitflow.payout.dto.PayoutBatchResponse;
import com.fitflow.payout.dto.TrainerEarningsResponse;
import com.fitflow.payout.entity.TrainerPayout;
import com.fitflow.payout.entity.TrainerSessionEarnings;
import com.fitflow.payout.model.EarningsStatus;
import com.fitflow.payout.model.PayoutStatus;
import com.fitflow.payout.repository.TrainerPayoutRepository;
import com.fitflow.payout.repository.TrainerSessionEarningsRepository;
import com.fitflow.security.TokenService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PayoutLifecycleIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrainerSessionEarningsRepository earningsRepository;

    @Autowired
    private TrainerPayoutRepository payoutRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.fitflow.fitnessclass.repository.FitnessClassRepository fitnessClassRepository;

    @Autowired
    private com.fitflow.session.repository.ClassSessionRepository sessionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private User trainer1;
    private String trainer1Token;
    private User trainer2;
    private String trainer2Token;
    private User admin1;
    private String admin1Token;
    private User admin2;
    private String admin2Token;

    @BeforeEach
    void setUp() {
        earningsRepository.deleteAll();
        payoutRepository.deleteAll();

        long stamp = clock.millis();

        trainer1 = userRepository.save(User.builder()
                .email("trainer1.payout." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Bob")
                .dateOfBirth(LocalDate.of(1988, 8, 8))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainer1.getId(), UserRole.Role.TRAINER)));
        trainer1Token = tokenService.generateAccessToken(trainer1.getId(), trainer1.getEmail(), List.of("TRAINER"), List.of());

        trainer2 = userRepository.save(User.builder()
                .email("trainer2.payout." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Alice")
                .dateOfBirth(LocalDate.of(1990, 9, 9))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainer2.getId(), UserRole.Role.TRAINER)));
        trainer2Token = tokenService.generateAccessToken(trainer2.getId(), trainer2.getEmail(), List.of("TRAINER"), List.of());

        admin1 = userRepository.save(User.builder()
                .email("admin1.payout." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Admin Creator")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(admin1.getId(), UserRole.Role.ADMIN)));
        admin1Token = tokenService.generateAccessToken(admin1.getId(), admin1.getEmail(), List.of("ADMIN"), List.of("PAYOUT_MANAGE"));

        admin2 = userRepository.save(User.builder()
                .email("admin2.payout." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Admin Approver")
                .dateOfBirth(LocalDate.of(1982, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(admin2.getId(), UserRole.Role.ADMIN)));
        admin2Token = tokenService.generateAccessToken(admin2.getId(), admin2.getEmail(), List.of("ADMIN"), List.of("PAYOUT_MANAGE"));
    }

    @Test
    @DisplayName("Payout Lifecycle: Create Draft Batch, Two-Person Approval, Mark Paid, and CSV Export")
    void testFullPayoutBatchLifecycle() throws Exception {
        Instant now = clock.instant();

        var s1 = createTestSession(trainer1.getId());
        var s2 = createTestSession(trainer1.getId());

        // Seed 2 session earnings for trainer 1
        TrainerSessionEarnings e1 = earningsRepository.save(TrainerSessionEarnings.builder()
                .sessionId(s1.getId())
                .trainerId(trainer1.getId())
                .amountPaise(150000L) // 1500 INR
                .status(EarningsStatus.PENDING)
                .createdAt(now)
                .build());

        TrainerSessionEarnings e2 = earningsRepository.save(TrainerSessionEarnings.builder()
                .sessionId(s2.getId())
                .trainerId(trainer1.getId())
                .amountPaise(200000L) // 2000 INR
                .status(EarningsStatus.PENDING)
                .createdAt(now)
                .build());

        // 1. Admin 1 creates draft batch
        LocalDate today = LocalDate.now(clock);
        CreatePayoutBatchRequest batchReq = new CreatePayoutBatchRequest(today.minusDays(1), today.plusDays(1));

        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/payouts")
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.totalPaise", is(350000)))
                .andExpect(jsonPath("$.sessionCount", is(2)))
                .andReturn();

        PayoutBatchResponse batch = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PayoutBatchResponse.class
        );

        // Verify earnings status transitioned to IN_BATCH
        assertEquals(EarningsStatus.IN_BATCH, earningsRepository.findById(e1.getId()).get().getStatus());
        assertEquals(EarningsStatus.IN_BATCH, earningsRepository.findById(e2.getId()).get().getStatus());

        // 2. Two-Person Rule: Admin 1 attempts to approve own batch -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/admin/payouts/" + batch.id() + "/approve")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Two-person rule violation")));

        // 3. Admin 2 approves batch -> 200 OK
        mockMvc.perform(post("/api/v1/admin/payouts/" + batch.id() + "/approve")
                        .header("Authorization", "Bearer " + admin2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.approvedBy", is(admin2.getId().intValue())));

        // 4. Mark Paid -> 200 OK
        mockMvc.perform(post("/api/v1/admin/payouts/" + batch.id() + "/mark-paid")
                        .header("Authorization", "Bearer " + admin2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")));

        // Verify earnings status transitioned to PAID
        assertEquals(EarningsStatus.PAID, earningsRepository.findById(e1.getId()).get().getStatus());
        assertEquals(EarningsStatus.PAID, earningsRepository.findById(e2.getId()).get().getStatus());

        // 5. CSV Export
        mockMvc.perform(get("/api/v1/admin/payouts/" + batch.id() + "/export.csv")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("payout-" + batch.id() + ".csv")))
                .andExpect(content().string(containsString("session_id,trainer_id,amount_paise,status,created_at")))
                .andExpect(content().string(containsString(s1.getId() + "," + trainer1.getId() + ",150000,PAID")))
                .andExpect(content().string(containsString(s2.getId() + "," + trainer1.getId() + ",200000,PAID")));
    }

    @Test
    @DisplayName("Trainer Isolation: Trainer sees only their own earnings")
    void testTrainerEarningsIsolation() throws Exception {
        Instant now = clock.instant();

        var s1 = createTestSession(trainer1.getId());
        var s2 = createTestSession(trainer2.getId());

        // Trainer 1 earning
        earningsRepository.save(TrainerSessionEarnings.builder()
                .sessionId(s1.getId())
                .trainerId(trainer1.getId())
                .amountPaise(100000L)
                .status(EarningsStatus.PENDING)
                .createdAt(now)
                .build());

        // Trainer 2 earning
        earningsRepository.save(TrainerSessionEarnings.builder()
                .sessionId(s2.getId())
                .trainerId(trainer2.getId())
                .amountPaise(250000L)
                .status(EarningsStatus.PENDING)
                .createdAt(now)
                .build());

        // Trainer 1 queries earnings
        MvcResult t1Result = mockMvc.perform(get("/api/v1/trainer/me/earnings")
                        .header("Authorization", "Bearer " + trainer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaise", is(100000)))
                .andExpect(jsonPath("$.pendingPaise", is(100000)))
                .andReturn();

        TrainerEarningsResponse t1Response = objectMapper.readValue(
                t1Result.getResponse().getContentAsString(), TrainerEarningsResponse.class
        );
        assertEquals(1, t1Response.items().size());
        assertEquals(s1.getId(), t1Response.items().get(0).sessionId());

        // Trainer 2 queries earnings
        MvcResult t2Result = mockMvc.perform(get("/api/v1/trainer/me/earnings")
                        .header("Authorization", "Bearer " + trainer2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaise", is(250000)))
                .andExpect(jsonPath("$.pendingPaise", is(250000)))
                .andReturn();

        TrainerEarningsResponse t2Response = objectMapper.readValue(
                t2Result.getResponse().getContentAsString(), TrainerEarningsResponse.class
        );
        assertEquals(1, t2Response.items().size());
        assertEquals(s2.getId(), t2Response.items().get(0).sessionId());
    }

    private com.fitflow.session.entity.ClassSession createTestSession(Long trainerId) {
        var fitnessClass = fitnessClassRepository.save(com.fitflow.fitnessclass.entity.FitnessClass.builder()
                .name("Session Class " + clock.millis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 5))
                .category("HIIT")
                .durationMinutes(45)
                .intensity(com.fitflow.fitnessclass.model.ClassIntensity.HIGH)
                .impactLevel(com.fitflow.fitnessclass.model.ClassImpactLevel.HIGH)
                .metValue(new java.math.BigDecimal("8.0"))
                .minAge(16)
                .defaultCapacity(15)
                .active(true)
                .metadataReviewed(true)
                .createdBy(admin1.getId())
                .build());

        return sessionRepository.save(com.fitflow.session.entity.ClassSession.builder()
                .classId(fitnessClass.getId())
                .trainerId(trainerId)
                .startsAt(clock.instant().minus(java.time.Duration.ofHours(2)))
                .endsAt(clock.instant().minus(java.time.Duration.ofHours(1)))
                .capacity(15)
                .bookedCount(5)
                .room("Studio B")
                .status(com.fitflow.session.entity.ClassSession.Status.COMPLETED)
                .trainerRateSnapshotPaise(100000L)
                .build());
    }
}
