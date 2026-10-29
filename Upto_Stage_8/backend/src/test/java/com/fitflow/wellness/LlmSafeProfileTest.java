package com.fitflow.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.security.EncryptionService;
import com.fitflow.user.AgeService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.LlmSafeProfile;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.HealthAccessLogRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSafeProfileTest {

    private ConsentService consentService;
    private MemberHealthProfileRepository profileRepository;
    private HealthDataCodec healthDataCodec;
    private ScreeningService screeningService;
    private UserRepository userRepository;
    private AgeService ageService;
    private HealthAccessService healthAccessService;
    private LlmSafeProfileProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Instant baseTime = Instant.parse("2026-10-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        consentService = mock(ConsentService.class);
        profileRepository = mock(MemberHealthProfileRepository.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        when(encryptionService.encrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());
        when(encryptionService.decrypt(any())).thenAnswer(inv -> ((byte[]) inv.getArgument(0)).clone());

        healthDataCodec = new HealthDataCodec(encryptionService, objectMapper);
        screeningService = new ScreeningService();
        userRepository = mock(UserRepository.class);
        ageService = mock(AgeService.class);
        HealthAccessLogRepository accessLogRepo = mock(HealthAccessLogRepository.class);
        Clock clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        healthAccessService = new HealthAccessService(accessLogRepo, clock);

        provider = new LlmSafeProfileProvider(
                consentService,
                profileRepository,
                healthDataCodec,
                screeningService,
                userRepository,
                ageService,
                healthAccessService
        );
    }

    @Test
    @DisplayName("Reflection Check: LlmSafeProfile contains ONLY the 8 allowed LLM-safe fields")
    void testReflectionOnlyAllowedFields() {
        Set<String> allowedFieldNames = Set.of(
                "ageBand",
                "sex",
                "experienceLevel",
                "goals",
                "injuryCodes",
                "conditionCodes",
                "screeningStatus",
                "doctorCheckRecommended"
        );

        Field[] fields = LlmSafeProfile.class.getDeclaredFields();
        Set<String> actualFields = Arrays.stream(fields)
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(allowedFieldNames, actualFields,
                "LlmSafeProfile must contain only the specified safe fields and never PII or raw health fields");

        // Verify forbidden fields do not exist
        Set<String> forbiddenFields = Set.of(
                "name", "fullName", "email", "height", "heightCm", "weight", "weightKg",
                "pastExperienceNote", "note", "notes", "parq", "parqAnswers", "age"
        );
        for (String forbidden : forbiddenFields) {
            assertFalse(actualFields.contains(forbidden), "LlmSafeProfile must not contain " + forbidden);
        }
    }

    @Test
    @DisplayName("JSON Snapshot: Serialization contains only permitted keys and matches schema")
    void testJsonSnapshot() throws Exception {
        LlmSafeProfile safeProfile = new LlmSafeProfile(
                "25-34",
                Sex.MALE,
                ExperienceLevel.INTERMEDIATE,
                List.of(Goal.GENERAL_FITNESS, Goal.BUILD_STRENGTH),
                List.of("KNEE:MODERATE"),
                List.of("HYPERTENSION"),
                ScreeningStatus.CAUTION,
                false
        );

        String json = objectMapper.writeValueAsString(safeProfile);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("25-34", root.get("ageBand").asText());
        assertEquals("MALE", root.get("sex").asText());
        assertEquals("INTERMEDIATE", root.get("experienceLevel").asText());
        assertEquals(2, root.get("goals").size());
        assertEquals("KNEE:MODERATE", root.get("injuryCodes").get(0).asText());
        assertEquals("HYPERTENSION", root.get("conditionCodes").get(0).asText());
        assertEquals("CAUTION", root.get("screeningStatus").asText());
        assertFalse(root.get("doctorCheckRecommended").asBoolean());

        // Ensure no extra properties were serialized
        assertEquals(8, root.size());
    }

    @Test
    @DisplayName("Returns empty Optional when AI_ASSISTANT_PROCESSING consent is missing")
    void testEmptyWithoutConsent() {
        Long memberId = 123L;
        when(consentService.hasConsent(memberId, ConsentPurpose.AI_ASSISTANT_PROCESSING)).thenReturn(false);

        Optional<LlmSafeProfile> result = provider.getLlmSafeProfile(memberId);
        assertTrue(result.isEmpty(), "Must return empty when AI_ASSISTANT_PROCESSING consent is not granted");
    }

    @Test
    @DisplayName("Returns populated LlmSafeProfile when AI_ASSISTANT_PROCESSING consent is granted")
    void testPopulatedWithConsent() {
        Long memberId = 123L;
        when(consentService.hasConsent(memberId, ConsentPurpose.AI_ASSISTANT_PROCESSING)).thenReturn(true);

        User user = User.builder()
                .email("user@example.com")
                .dateOfBirth(LocalDate.of(1996, 5, 20)) // 30 years old
                .status(User.Status.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", memberId);
        when(userRepository.findById(memberId)).thenReturn(Optional.of(user));
        when(ageService.calculateAge(any())).thenReturn(30);

        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.ADVANCED,
                "Free text note that should be omitted",
                List.of(Goal.LOSE_WEIGHT),
                4,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.ANKLE, InjurySeverity.MILD)),
                Set.of(HealthCondition.ASTHMA),
                Map.of("Q1", false),
                false,
                new BigDecimal("175.0"),
                new BigDecimal("68.0"),
                Sex.FEMALE,
                null,
                1
        );

        byte[] enc = healthDataCodec.encode(profile);
        MemberHealthProfile entity = MemberHealthProfile.builder()
                .memberId(memberId)
                .dataEncrypted(enc)
                .schemaVersion(1)
                .version(1)
                .createdAt(Instant.now())
                .build();
        when(profileRepository.findByMemberId(memberId)).thenReturn(Optional.of(entity));

        Optional<LlmSafeProfile> result = provider.getLlmSafeProfile(memberId);
        assertTrue(result.isPresent());

        LlmSafeProfile safe = result.get();
        assertEquals("25-34", safe.ageBand());
        assertEquals(Sex.FEMALE, safe.sex());
        assertEquals(ExperienceLevel.ADVANCED, safe.experienceLevel());
        assertEquals(List.of(Goal.LOSE_WEIGHT), safe.goals());
        assertEquals(List.of("ANKLE:MILD"), safe.injuryCodes());
        assertEquals(List.of("ASTHMA"), safe.conditionCodes());
        assertEquals(ScreeningStatus.CAUTION, safe.screeningStatus());
        assertFalse(safe.doctorCheckRecommended());
    }
}
