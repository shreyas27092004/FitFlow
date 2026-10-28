package com.fitflow.trainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.admin.entity.AdminActionLog;
import com.fitflow.admin.repository.AdminActionLogRepository;
import com.fitflow.security.TokenService;
import com.fitflow.trainer.dto.AvailabilitySlotDto;
import com.fitflow.trainer.dto.UpdateTrainerProfileRequest;
import com.fitflow.trainer.dto.UpdateTrainerRateRequest;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TrainerProfileIT {

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
    private AdminPermissionRepository adminPermissionRepository;

    @Autowired
    private AdminActionLogRepository adminActionLogRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User trainerUser;
    private TrainerProfile trainerProfile;
    private String trainerToken;

    private User memberUser;
    private String memberToken;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        trainerProfileRepository.deleteAll();
        // 1. Trainer
        trainerUser = userRepository.save(User.builder()
                .email("coach.sam@fitflow.local")
                .passwordHash(passwordEncoder.encode("CoachPass123!"))
                .fullName("Sam Coach")
                .phone("+919123456780")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));
        trainerProfile = trainerProfileRepository.save(TrainerProfile.builder()
                .userId(trainerUser.getId())
                .bio("HIIT and functional fitness specialist with 8 years experience")
                .specialties("[\"HIIT\",\"Strength\",\"Mobility\"]")
                .certifications("[\"ACE-CPT\",\"CSCS\"]")
                .ratePerSessionPaise(75000L) // 750 INR
                .availability("[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"12:00\"}]")
                .active(true)
                .build());
        trainerToken = tokenService.generateAccessToken(
                trainerUser.getId(), trainerUser.getEmail(), List.of("TRAINER"), List.of()
        );

        // 2. Member
        memberUser = userRepository.save(User.builder()
                .email("trainer.member@fitflow.local")
                .passwordHash(passwordEncoder.encode("MemberPass123!"))
                .fullName("Member Viewer")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUser.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(
                memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of()
        );

        // 3. Admin with PAYOUT_APPROVE
        adminUser = userRepository.save(User.builder()
                .email("payout.admin@fitflow.local")
                .passwordHash(passwordEncoder.encode("AdminPass123!"))
                .fullName("Payout Admin")
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(adminUser.getId(), UserRole.Role.ADMIN)));
        adminPermissionRepository.save(AdminPermission.builder()
                .id(new AdminPermission.AdminPermissionId(adminUser.getId(), "PAYOUT_APPROVE"))
                .grantedBy(adminUser.getId())
                .grantedAt(Instant.now())
                .build());
        adminToken = tokenService.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), List.of("ADMIN"), List.of("PAYOUT_APPROVE")
        );
    }

    @Test
    @DisplayName("GET /api/v1/trainers: unauthenticated returns 401")
    void testListTrainersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/trainers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("GET /api/v1/trainers: member view returns active trainers without rate or email")
    void testListTrainersPublicView() throws Exception {
        mockMvc.perform(get("/api/v1/trainers")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()))
                .andExpect(jsonPath("$[0].fullName", is("Sam Coach")))
                .andExpect(jsonPath("$[0].bio", containsString("HIIT")))
                .andExpect(jsonPath("$[0].specialties", hasSize(3)))
                .andExpect(jsonPath("$[0].ratePerSessionPaise").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/trainers/{id}: public view returns trainer without rate or email")
    void testGetPublicTrainerSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/trainers/" + trainerUser.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(trainerUser.getId().intValue())))
                .andExpect(jsonPath("$.fullName", is("Sam Coach")))
                .andExpect(jsonPath("$[0].ratePerSessionPaise").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/trainers/{id}: non-existent trainer returns 404")
    void testGetPublicTrainerNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/trainers/999999")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/trainer/me: member calling returns 403 FORBIDDEN")
    void testGetTrainerMeForbiddenForMember() throws Exception {
        mockMvc.perform(get("/api/v1/trainer/me")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("GET /api/v1/trainer/me: trainer returns full profile including pay rate")
    void testGetTrainerMeSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/trainer/me")
                        .header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(trainerUser.getId().intValue())))
                .andExpect(jsonPath("$.email", is("coach.sam@fitflow.local")))
                .andExpect(jsonPath("$.phone", is("+919123456780")))
                .andExpect(jsonPath("$.ratePerSessionPaise", is(75000)))
                .andExpect(jsonPath("$.availability", hasSize(1)))
                .andExpect(jsonPath("$.version", notNullValue()));
    }

    @Test
    @DisplayName("PATCH /api/v1/trainer/me: trainer sending ratePerSessionPaise returns 403 FORBIDDEN")
    void testPatchTrainerMeWithRateForbidden() throws Exception {
        UpdateTrainerProfileRequest request = new UpdateTrainerProfileRequest(
                "New Bio",
                null,
                null,
                null,
                90000L, // Trying to self-raise rate to 900 INR
                trainerProfile.getVersion()
        );

        mockMvc.perform(patch("/api/v1/trainer/me")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")))
                .andExpect(jsonPath("$.detail", containsString("Trainers cannot modify their own session rates")));
    }

    @Test
    @DisplayName("PATCH /api/v1/trainer/me: invalid overlapping availability returns 400 VALIDATION_FAILED")
    void testPatchTrainerMeInvalidAvailabilityFails() throws Exception {
        List<AvailabilitySlotDto> badSlots = List.of(
                new AvailabilitySlotDto(1, "09:00", "12:00"),
                new AvailabilitySlotDto(1, "11:00", "14:00") // Overlaps
        );
        UpdateTrainerProfileRequest request = new UpdateTrainerProfileRequest(
                null, null, null, badSlots, null, trainerProfile.getVersion()
        );

        mockMvc.perform(patch("/api/v1/trainer/me")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.detail", containsString("Overlapping availability slots detected")));
    }

    @Test
    @DisplayName("PATCH /api/v1/trainer/me: version mismatch returns 409 OPTIMISTIC_LOCK")
    void testPatchTrainerMeOptimisticLockMismatch() throws Exception {
        UpdateTrainerProfileRequest request = new UpdateTrainerProfileRequest(
                "Updated Bio", null, null, null, null, trainerProfile.getVersion() + 10
        );

        mockMvc.perform(patch("/api/v1/trainer/me")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("OPTIMISTIC_LOCK")));
    }

    @Test
    @DisplayName("PATCH /api/v1/trainer/me: valid bio and availability succeeds")
    void testPatchTrainerMeSuccess() throws Exception {
        List<AvailabilitySlotDto> validSlots = List.of(
                new AvailabilitySlotDto(1, "08:00", "11:00"),
                new AvailabilitySlotDto(1, "13:00", "17:00")
        );
        UpdateTrainerProfileRequest request = new UpdateTrainerProfileRequest(
                "Updated Bio by Trainer",
                List.of("HIIT", "Cardio"),
                List.of("ACE-CPT", "Yoga-200"),
                validSlots,
                null,
                trainerProfile.getVersion()
        );

        mockMvc.perform(patch("/api/v1/trainer/me")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio", is("Updated Bio by Trainer")))
                .andExpect(jsonPath("$.specialties", hasSize(2)))
                .andExpect(jsonPath("$.availability", hasSize(2)));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/trainers/{id}/rate: requires PAYOUT_APPROVE (MEMBER returns 403)")
    void testUpdateTrainerRateForbiddenForMember() throws Exception {
        UpdateTrainerRateRequest request = new UpdateTrainerRateRequest(85000L, trainerProfile.getVersion());

        mockMvc.perform(patch("/api/v1/admin/trainers/" + trainerUser.getId() + "/rate")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/trainers/{id}/rate: admin with PAYOUT_APPROVE updates rate and writes audit log")
    void testUpdateTrainerRateSuccess() throws Exception {
        UpdateTrainerRateRequest request = new UpdateTrainerRateRequest(95000L, trainerProfile.getVersion());

        mockMvc.perform(patch("/api/v1/admin/trainers/" + trainerUser.getId() + "/rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePerSessionPaise", is(95000)));

        TrainerProfile updated = trainerProfileRepository.findById(trainerUser.getId()).orElseThrow();
        assertEquals(95000L, updated.getRatePerSessionPaise());

        List<AdminActionLog> logs = adminActionLogRepository.findAll().stream()
                .filter(l -> "TRAINER_RATE_UPDATE".equals(l.getAction()) && l.getTargetId().equals(trainerUser.getId()))
                .toList();
        assertEquals(1, logs.size());
    }

    @Test
    @DisplayName("GET /api/v1/trainer/me/summary: returns upcomingSessionsCount=0 and earnings=null (Stage 5 placeholder)")
    void testGetTrainerSummarySuccess() throws Exception {
        mockMvc.perform(get("/api/v1/trainer/me/summary")
                        .header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingSessionsCount", is(0)))
                .andExpect(jsonPath("$.earnings", nullValue()));
    }
}
