package com.fitflow.wellness;

import com.fitflow.common.error.ApiException;
import com.fitflow.security.EncryptionService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.wellness.dto.BodyMetricRequest;
import com.fitflow.wellness.dto.BodyMetricResponse;
import com.fitflow.wellness.dto.ClassFeedbackRequest;
import com.fitflow.wellness.dto.ConsentInfoResponse;
import com.fitflow.wellness.dto.DeleteHealthDataRequest;
import com.fitflow.wellness.dto.FeedbackResponse;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.GrantConsentRequest;
import com.fitflow.wellness.dto.GrantedPurposeDto;
import com.fitflow.wellness.dto.HealthExportResponse;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.LlmSafeProfile;
import com.fitflow.wellness.dto.MissedSessionDto;
import com.fitflow.wellness.dto.NutritionNumbersResponse;
import com.fitflow.wellness.dto.ProfileResponse;
import com.fitflow.wellness.dto.RestrictionProposalDto;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.dto.SafetyVerdict;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.dto.SuggestedModificationDto;
import com.fitflow.wellness.dto.TrainerPrepResponse;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import com.fitflow.wellness.dto.WellnessMetadataResponse;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.SafetyVerdictType;
import com.fitflow.wellness.model.ScreeningStatus;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HealthProfileStorageTest {

    @Autowired
    private MemberHealthProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HealthDataCodec healthDataCodec;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Test
    @DisplayName("Raw SQL read of data_encrypted confirms ciphertext contains none of the plaintext words")
    void testRawSqlReadContainsNoPlaintext() {
        User user = userRepository.save(User.builder()
                .email("health.storage." + System.currentTimeMillis() + "@example.com")
                .fullName("Health Storage User")
                .passwordHash("hash")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        Long testMemberId = user.getId();
        String secretNote = "PrivateRecoveringFromACLReconstruction2026";

        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.ADVANCED,
                secretNote,
                List.of(Goal.REHAB_FRIENDLY),
                2,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.MODERATE)),
                Set.of(HealthCondition.HYPERTENSION),
                Map.of("Q1", false, "Q2", false),
                false,
                new BigDecimal("182.5"),
                new BigDecimal("78.0"),
                Sex.MALE,
                null,
                1
        );

        byte[] cipherBlob = healthDataCodec.encode(profile);
        assertNotNull(cipherBlob);

        // Delete any leftover profile and insert directly
        profileRepository.deleteByMemberId(testMemberId);
        MemberHealthProfile entity = MemberHealthProfile.builder()
                .memberId(testMemberId)
                .dataEncrypted(cipherBlob)
                .schemaVersion(1)
                .version(0)
                .createdAt(Instant.now())
                .build();
        profileRepository.saveAndFlush(entity);

        // Perform raw SQL read on MySQL
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        byte[] rawBytesFromDb = jdbcTemplate.queryForObject(
                "SELECT data_encrypted FROM member_health_profiles WHERE member_id = ?",
                (rs, rowNum) -> rs.getBytes("data_encrypted"),
                testMemberId
        );

        assertNotNull(rawBytesFromDb);
        String rawBlobString = new String(rawBytesFromDb);

        // Assert NONE of the plaintext health terms or notes appear in raw database storage
        assertFalse(rawBlobString.contains("KNEE"), "Raw DB blob must not contain 'KNEE'");
        assertFalse(rawBlobString.contains("MODERATE"), "Raw DB blob must not contain 'MODERATE'");
        assertFalse(rawBlobString.contains("HYPERTENSION"), "Raw DB blob must not contain 'HYPERTENSION'");
        assertFalse(rawBlobString.contains(secretNote), "Raw DB blob must not contain past experience note");
        assertFalse(rawBlobString.contains("182.5"), "Raw DB blob must not contain plaintext height");
        assertFalse(rawBlobString.contains("78.0"), "Raw DB blob must not contain plaintext weight");
    }

    @Test
    @DisplayName("Tampered byte in ciphertext fails with safe ApiException")
    void testTamperedByteFailsSafely() {
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.BEGINNER,
                "Note",
                List.of(Goal.GENERAL_FITNESS),
                2,
                Collections.emptyList(),
                List.of(),
                Set.of(),
                Map.of(),
                false,
                new BigDecimal("170.0"),
                new BigDecimal("65.0"),
                Sex.FEMALE,
                null,
                1
        );

        byte[] cipherBlob = healthDataCodec.encode(profile);
        assertNotNull(cipherBlob);

        // Tamper with the last byte
        byte[] tampered = cipherBlob.clone();
        tampered[tampered.length - 1] ^= 0xFF;

        ApiException ex = assertThrows(ApiException.class, () -> healthDataCodec.decode(tampered));
        assertTrue(ex.getMessage().contains("integrity check failed or payload corrupted"),
                "Safe error message expected on tampering");
    }

    @Test
    @DisplayName("Reflection check over all wellness DTOs asserting toString() contains no field values")
    void testWellnessDtosRedactedToString() {
        // Test FitnessProfile
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.ADVANCED,
                "TopSecretMedicalNote123",
                List.of(Goal.REHAB_FRIENDLY),
                2,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.SEVERE)),
                Set.of(HealthCondition.HYPERTENSION),
                Map.of("Q1", true),
                true,
                new BigDecimal("185.0"),
                new BigDecimal("80.0"),
                Sex.MALE,
                null,
                1
        );
        String profileStr = profile.toString();
        assertFalse(profileStr.contains("TopSecretMedicalNote123"));
        assertFalse(profileStr.contains("KNEE"));
        assertFalse(profileStr.contains("SEVERE"));
        assertFalse(profileStr.contains("HYPERTENSION"));
        assertFalse(profileStr.contains("185.0"));
        assertFalse(profileStr.contains("80.0"));
        assertTrue(profileStr.contains("[REDACTED]"));

        // Test InjuryDto
        InjuryDto injury = new InjuryDto(InjuryArea.SHOULDER, InjurySeverity.MODERATE);
        assertFalse(injury.toString().contains("SHOULDER"));
        assertFalse(injury.toString().contains("MODERATE"));
        assertTrue(injury.toString().contains("[REDACTED]"));

        // Test ScreeningResult
        ScreeningResult screening = new ScreeningResult(ScreeningStatus.RED_FLAG, List.of("PARQ_YES:Q1"), true);
        assertFalse(screening.toString().contains("RED_FLAG"));
        assertFalse(screening.toString().contains("PARQ_YES:Q1"));
        assertTrue(screening.toString().contains("[REDACTED]"));

        // Test SafetyVerdict
        SafetyVerdict verdict = new SafetyVerdict(SafetyVerdictType.BLOCKED, List.of("TAG_CONFLICT:high_impact"), List.of("Avoid high impact"), "Disclaimer");
        assertFalse(verdict.toString().contains("high_impact"));
        assertTrue(verdict.toString().contains("[REDACTED]"));

        // Test ProfileResponse
        ProfileResponse profileResp = new ProfileResponse(profile, screening);
        assertFalse(profileResp.toString().contains("TopSecretMedicalNote123"));
        assertTrue(profileResp.toString().contains("[REDACTED]"));

        // Test BodyMetricRequest & BodyMetricResponse
        BodyMetricRequest metricReq = new BodyMetricRequest(LocalDate.now(), new BigDecimal("75.5"), new BigDecimal("178.0"));
        assertFalse(metricReq.toString().contains("75.5"));
        assertTrue(metricReq.toString().contains("[REDACTED]"));

        BodyMetricResponse metricResp = new BodyMetricResponse(1L, LocalDate.now(), new BigDecimal("75.5"), new BigDecimal("178.0"), Instant.now());
        assertFalse(metricResp.toString().contains("75.5"));
        assertTrue(metricResp.toString().contains("[REDACTED]"));

        // Test ClassFeedbackRequest & FeedbackResponse
        ClassFeedbackRequest fbReq = new ClassFeedbackRequest(5, true, InjuryArea.HIP, "Very sharp pain in hip joint");
        assertFalse(fbReq.toString().contains("Very sharp pain in hip joint"));
        assertFalse(fbReq.toString().contains("HIP"));
        assertTrue(fbReq.toString().contains("[REDACTED]"));

        FeedbackResponse fbResp = new FeedbackResponse(10L, "Would you like to add this?");
        assertTrue(fbResp.toString().contains("[REDACTED]"));

        // Test LlmSafeProfile
        LlmSafeProfile llmSafe = new LlmSafeProfile("25-34", Sex.FEMALE, ExperienceLevel.BEGINNER, List.of(Goal.LOSE_WEIGHT), List.of("KNEE:MILD"), List.of(), ScreeningStatus.CAUTION, false);
        assertFalse(llmSafe.toString().contains("25-34"));
        assertFalse(llmSafe.toString().contains("KNEE:MILD"));
        assertTrue(llmSafe.toString().contains("[REDACTED]"));

        // Test other DTOs
        assertTrue(new ConsentInfoResponse("v1", "text", List.of(ConsentPurpose.PERSONALIZED_CLASSES), List.of(), true).toString().contains("[REDACTED]"));
        assertTrue(new GrantConsentRequest("v1", List.of(ConsentPurpose.CALORIE_ESTIMATES)).toString().contains("[REDACTED]"));
        assertTrue(new GrantedPurposeDto(ConsentPurpose.CALORIE_ESTIMATES, Instant.now()).toString().contains("[REDACTED]"));
        assertTrue(new DeleteHealthDataRequest("DELETE MY HEALTH DATA").toString().contains("[REDACTED]"));
        assertTrue(new MissedSessionDto(1L, Instant.now(), "Class").toString().contains("[REDACTED]"));
        assertTrue(new WeeklyProgressResponse(1, 1, 0, 1, 200, List.of()).toString().contains("[REDACTED]"));
        assertTrue(new RestrictionProposalDto(1L, InjuryArea.ANKLE, InjurySeverity.MILD, "PENDING", Instant.now()).toString().contains("[REDACTED]"));
        assertTrue(new SuggestedModificationDto("LOW_IMPACT", "Low Impact").toString().contains("[REDACTED]"));
        assertTrue(new TrainerPrepResponse(5, true, Map.of("KNEE", 2), List.of(), null).toString().contains("[REDACTED]"));
        assertTrue(new NutritionNumbersResponse(true, null, 1500, 2000, 1800, false, List.of(), "disc").toString().contains("[REDACTED]"));
        assertTrue(new SafeClassesResponse(false, "disc", null, List.of(), List.of()).toString().contains("[REDACTED]"));
        assertTrue(new WellnessMetadataResponse(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()).toString().contains("[REDACTED]"));
        assertTrue(new HealthExportResponse(null, List.of(), List.of(), List.of(), List.of(), List.of()).toString().contains("[REDACTED]"));
    }
}
