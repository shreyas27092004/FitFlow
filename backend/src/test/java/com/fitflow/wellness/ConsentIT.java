package com.fitflow.wellness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.security.TokenService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.GrantConsentRequest;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.HealthConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsentIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HealthConsentRepository consentRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper objectMapper;

    private User adultMember;
    private String adultToken;

    private User minorMember;
    private String minorToken;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now(clock);

        // Adult: 25 years old
        adultMember = userRepository.save(User.builder()
                .email("adult." + System.currentTimeMillis() + "@example.com")
                .fullName("Adult Member")
                .passwordHash("hashed")
                .dateOfBirth(today.minusYears(25))
                .status(User.Status.ACTIVE)
                .build());
        adultToken = "Bearer " + tokenService.generateAccessToken(adultMember.getId(), adultMember.getEmail(), List.of("MEMBER"), List.of());

        // Minor: 18 years minus 1 day (17 years and 364 days old)
        minorMember = userRepository.save(User.builder()
                .email("minor." + System.currentTimeMillis() + "@example.com")
                .fullName("Minor Member")
                .passwordHash("hashed")
                .dateOfBirth(today.minusYears(18).plusDays(1))
                .status(User.Status.ACTIVE)
                .build());
        minorToken = "Bearer " + tokenService.generateAccessToken(minorMember.getId(), minorMember.getEmail(), List.of("MEMBER"), List.of());
    }

    @Test
    @DisplayName("POST consent: 17-year-old (one day short of 18) rejected with 403 AGE_RESTRICTED")
    void test17YearOldRejectedFromGrantingConsent() throws Exception {
        GrantConsentRequest req = new GrantConsentRequest("v1", List.of(ConsentPurpose.PERSONALIZED_CLASSES));

        mockMvc.perform(post("/api/v1/wellness/consent")
                        .header("Authorization", minorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AGE_RESTRICTED"));
    }

    @Test
    @DisplayName("POST consent: Wrong version returns 409 CONFLICT")
    void testWrongVersionReturns409() throws Exception {
        GrantConsentRequest req = new GrantConsentRequest("v2", List.of(ConsentPurpose.PERSONALIZED_CLASSES));

        mockMvc.perform(post("/api/v1/wellness/consent")
                        .header("Authorization", adultToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("DELETE consent revokes purpose and subsequent access returns 403 CONSENT_REQUIRED")
    void testRevokeStopsAccess() throws Exception {
        // 1. Grant consent
        GrantConsentRequest grantReq = new GrantConsentRequest("v1", List.of(ConsentPurpose.PERSONALIZED_CLASSES, ConsentPurpose.CALORIE_ESTIMATES));
        mockMvc.perform(post("/api/v1/wellness/consent")
                        .header("Authorization", adultToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk());

        // 2. Save health profile
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.BEGINNER, "Notes", List.of(Goal.GENERAL_FITNESS), 2,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptyMap(),
                false, new BigDecimal("175.0"), new BigDecimal("70.0"), Sex.MALE, null, 1
        );
        mockMvc.perform(put("/api/v1/wellness/profile")
                        .header("Authorization", adultToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk());

        // 3. Nutrition numbers works
        mockMvc.perform(get("/api/v1/wellness/nutrition/numbers")
                        .header("Authorization", adultToken))
                .andExpect(status().isOk());

        // 4. Revoke CALORIE_ESTIMATES
        mockMvc.perform(delete("/api/v1/wellness/consent/CALORIE_ESTIMATES")
                        .header("Authorization", adultToken))
                .andExpect(status().isNoContent());

        // 5. Nutrition numbers now returns 403 CONSENT_REQUIRED
        mockMvc.perform(get("/api/v1/wellness/nutrition/numbers")
                        .header("Authorization", adultToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/wellness/profile",
            "GET, /api/v1/wellness/nutrition/numbers",
            "GET, /api/v1/wellness/classes/safe"
    })
    @DisplayName("Every wellness route returns 403 CONSENT_REQUIRED when required consent is missing")
    void testAllWellnessRoutesReturn403WithoutConsent(String method, String path) throws Exception {
        // Adult user with ZERO consent granted
        if ("GET".equalsIgnoreCase(method)) {
            mockMvc.perform(get(path)
                            .header("Authorization", adultToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        }
    }
}
