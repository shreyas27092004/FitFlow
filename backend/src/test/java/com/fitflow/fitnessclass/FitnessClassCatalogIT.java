package com.fitflow.fitnessclass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.fitnessclass.dto.CreateClassRequest;
import com.fitflow.fitnessclass.dto.UpdateClassRequest;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.security.TokenService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FitnessClassCatalogIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private String adminToken;
    private String memberToken;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        classSessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();
        User adminUser = User.builder()
                .email("classadmin" + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Class Admin")
                .dateOfBirth(LocalDate.of(1985, 1, 1))
                .status(User.Status.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminUserId = adminUser.getId();

        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(adminUserId, UserRole.Role.ADMIN)));
        adminToken = tokenService.generateAccessToken(adminUser.getId(), adminUser.getEmail(), List.of("ADMIN"), List.of("CLASS_EDIT", "SUPER_ADMIN"));

        User memberUser = User.builder()
                .email("classmember" + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Class Member")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status(User.Status.ACTIVE)
                .build();
        memberUser = userRepository.save(memberUser);
        Long memberUserId = memberUser.getId();

        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(memberUserId, UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(memberUser.getId(), memberUser.getEmail(), List.of("MEMBER"), List.of());
    }

    @Test
    @DisplayName("GET /api/v1/classes/metadata returns all defined vocabularies")
    void testGetMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/classes/metadata")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intensity", hasItems("LOW", "MODERATE", "HIGH")))
                .andExpect(jsonPath("$.impactLevel", hasItems("LOW", "MEDIUM", "HIGH")))
                .andExpect(jsonPath("$.categories", hasItems("YOGA", "PILATES", "HIIT", "STRENGTH", "CARDIO", "DANCE", "MOBILITY", "BOXING")))
                .andExpect(jsonPath("$.contraindicationTags", hasItems(
                        "high_impact", "heavy_lifting", "deep_flexion", "heated_room", "inversion", "high_cardio", "balance_risk"
                )));
    }

    @Test
    @DisplayName("POST /api/v1/admin/classes with invalid contraindication tag returns 400 VALIDATION_FAILED")
    void testCreateClassInvalidTag() throws Exception {
        CreateClassRequest req = new CreateClassRequest(
                "Power HIIT Blast",
                "High energy session",
                ClassCategory.HIIT,
                45,
                ClassIntensity.HIGH,
                ClassImpactLevel.HIGH,
                new BigDecimal("9.5"),
                List.of("high_impact", "invalid_contraindication_tag"),
                16,
                25
        );

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/classes forbidden for MEMBER")
    void testCreateClassForbiddenForMember() throws Exception {
        CreateClassRequest req = new CreateClassRequest(
                "Gentle Yoga",
                "Relaxing yoga",
                ClassCategory.YOGA,
                60,
                ClassIntensity.LOW,
                ClassImpactLevel.LOW,
                new BigDecimal("3.0"),
                List.of("heated_room"),
                14,
                20
        );

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/admin/classes success as ADMIN sets metadataReviewed=true")
    void testCreateClassSuccess() throws Exception {
        CreateClassRequest req = new CreateClassRequest(
                "Ashtanga Yoga Intro",
                "Traditional flow",
                ClassCategory.YOGA,
                60,
                ClassIntensity.MODERATE,
                ClassImpactLevel.LOW,
                new BigDecimal("4.0"),
                List.of("deep_flexion", "inversion"),
                14,
                20
        );

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Ashtanga Yoga Intro")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.metadataReviewed", is(true)))
                .andExpect(jsonPath("$.contraindicationTags", hasItems("deep_flexion", "inversion")));
    }

    @Test
    @DisplayName("GET /api/v1/classes filters active=true AND metadataReviewed=true for MEMBER, but ADMIN sees all")
    void testCatalogVisibility() throws Exception {
        // Class 1: Active + Reviewed
        FitnessClass c1 = fitnessClassRepository.save(FitnessClass.builder()
                .name("Class Reviewed & Active")
                .category("YOGA")
                .durationMinutes(60)
                .intensity(ClassIntensity.LOW)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("3.0"))
                .active(true)
                .metadataReviewed(true)
                .createdBy(adminUserId)
                .build());

        // Class 2: Active + NOT Reviewed (e.g. created by AI assistant)
        FitnessClass c2 = fitnessClassRepository.save(FitnessClass.builder()
                .name("Class Pending Review")
                .category("HIIT")
                .durationMinutes(30)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(new BigDecimal("10.0"))
                .active(true)
                .metadataReviewed(false)
                .createdBy(adminUserId)
                .build());

        // Class 3: Deactivated + Reviewed
        FitnessClass c3 = fitnessClassRepository.save(FitnessClass.builder()
                .name("Class Deactivated")
                .category("PILATES")
                .durationMinutes(45)
                .intensity(ClassIntensity.MODERATE)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("4.5"))
                .active(false)
                .metadataReviewed(true)
                .createdBy(adminUserId)
                .build());

        // Member query -> only Class 1
        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name", is("Class Reviewed & Active")));

        // Admin query -> all 3
        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)));

        // Member GET /api/v1/classes/{id} for pending review -> 404
        mockMvc.perform(get("/api/v1/classes/" + c2.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());

        // Admin GET /api/v1/classes/{id} for pending review -> 200
        mockMvc.perform(get("/api/v1/classes/" + c2.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Class Pending Review")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/classes/{id} with mismatched version returns 409 OPTIMISTIC_LOCK")
    void testOptimisticLockFailure() throws Exception {
        FitnessClass fc = fitnessClassRepository.save(FitnessClass.builder()
                .name("Core Blast")
                .category("STRENGTH")
                .durationMinutes(45)
                .intensity(ClassIntensity.MODERATE)
                .impactLevel(ClassImpactLevel.MEDIUM)
                .metValue(new BigDecimal("6.0"))
                .active(true)
                .metadataReviewed(true)
                .createdBy(adminUserId)
                .build());

        UpdateClassRequest updateReq = new UpdateClassRequest(
                "Core Blast Updated",
                "New description",
                ClassCategory.STRENGTH,
                50,
                ClassIntensity.HIGH,
                ClassImpactLevel.HIGH,
                new BigDecimal("7.0"),
                List.of("heavy_lifting"),
                16,
                25,
                999 // wrong version
        );

        mockMvc.perform(put("/api/v1/admin/classes/" + fc.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("OPTIMISTIC_LOCK")));
    }
}
