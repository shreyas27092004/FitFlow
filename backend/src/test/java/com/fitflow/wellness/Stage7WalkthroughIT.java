package com.fitflow.wellness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.fitflow.security.TokenService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.wellness.dto.ClassFeedbackRequest;
import com.fitflow.wellness.dto.DeleteHealthDataRequest;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.GrantConsentRequest;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.entity.RestrictionProposal;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.Sex;
import com.fitflow.wellness.repository.HealthConsentRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import com.fitflow.wellness.repository.RestrictionProposalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
public class Stage7WalkthroughIT {

    private static final Logger log = LoggerFactory.getLogger(Stage7WalkthroughIT.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private RestrictionProposalRepository proposalRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MemberHealthProfileRepository profileRepository;

    @Autowired
    private HealthConsentRepository consentRepository;

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("Stage 7 End-to-End Walkthrough: 9 Steps demonstrating all compliance, safety, calorie math, and DPDP rights")
    void testCompleteStage7Walkthrough() throws Exception {
        log.info("========== STAGE 7 LIVE WALKTHROUGH START ==========");

        // 1. Over-18 member reads consent (GET /api/v1/wellness/consent)
        User member = userRepository.save(User.builder()
                .email("walkthrough.member." + System.currentTimeMillis() + "@fitflow.test")
                .fullName("Walkthrough Test Member")
                .passwordHash("hashed")
                .dateOfBirth(LocalDate.now(clock).minusYears(28)) // 28 years old
                .status(User.Status.ACTIVE)
                .build());
        String memberToken = tokenService.generateAccessToken(member.getId(), member.getEmail(), List.of("MEMBER"), List.of());

        MvcResult consentResult = mockMvc.perform(get("/api/v1/wellness/consent")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.eligible").value(true))
                .andReturn();
        String consentBody = consentResult.getResponse().getContentAsString();
        assertTrue(consentBody.contains("Consent wording should be confirmed against your institution's requirements and India's DPDP Act obligations."));
        log.info("Step 1 PASSED: Over-18 member read consent. Response:\n{}", consentBody);

        // 2. Member grants PERSONALIZED_CLASSES and CALORIE_ESTIMATES
        GrantConsentRequest grantReq = new GrantConsentRequest("v1", List.of(
                ConsentPurpose.PERSONALIZED_CLASSES,
                ConsentPurpose.CALORIE_ESTIMATES
        ));
        mockMvc.perform(post("/api/v1/wellness/consent")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isOk());
        log.info("Step 2 PASSED: Member granted PERSONALIZED_CLASSES and CALORIE_ESTIMATES");

        // 3. Member saves health profile with MODERATE knee injury
        FitnessProfile profile = new FitnessProfile(
                ExperienceLevel.INTERMEDIATE,
                "Past ACL surgery on right knee in 2023",
                List.of(Goal.LOSE_WEIGHT),
                3,
                Collections.emptyList(),
                List.of(new InjuryDto(InjuryArea.KNEE, InjurySeverity.MODERATE)),
                Set.of(),
                Map.of("Q1", false, "Q2", false, "Q3", false, "Q4", false, "Q5", false, "Q6", false, "Q7", false),
                true,
                new BigDecimal("175.0"),
                new BigDecimal("70.0"),
                Sex.MALE,
                null,
                1
        );
        mockMvc.perform(put("/api/v1/wellness/profile")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screening.status").value("CAUTION"));
        log.info("Step 3 PASSED: Member saved profile with MODERATE knee injury and screening status CAUTION");

        // 4. Safe class sessions check (GET /api/v1/wellness/classes/safe)
        FitnessClass hiitClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("High-Intensity Bootcamp " + System.currentTimeMillis())
                .description("Intense high-impact circuit training")
                .category("HIIT")
                .durationMinutes(50)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(new BigDecimal("8.0"))
                .contraindicationTags("[\"high_impact\",\"deep_flexion\"]")
                .metadataReviewed(true)
                .createdBy(member.getId())
                .build());

        FitnessClass yogaClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Gentle Flow Yoga " + System.currentTimeMillis())
                .description("Low-impact restorative yoga")
                .category("YOGA")
                .durationMinutes(50)
                .intensity(ClassIntensity.LOW)
                .impactLevel(ClassImpactLevel.LOW)
                .metValue(new BigDecimal("3.0"))
                .contraindicationTags("[]")
                .metadataReviewed(true)
                .createdBy(member.getId())
                .build());

        Instant sessionStart = clock.instant().plus(Duration.ofDays(1));
        ClassSession hiitSession = sessionRepository.save(ClassSession.builder()
                .classId(hiitClass.getId())
                .trainerId(member.getId())
                .startsAt(sessionStart)
                .endsAt(sessionStart.plus(Duration.ofMinutes(50)))
                .capacity(20)
                .bookedCount(0)
                .room("Studio A")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        ClassSession yogaSession = sessionRepository.save(ClassSession.builder()
                .classId(yogaClass.getId())
                .trainerId(member.getId())
                .startsAt(sessionStart.plus(Duration.ofHours(2)))
                .endsAt(sessionStart.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(50)))
                .capacity(20)
                .bookedCount(0)
                .room("Studio B")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(50000L)
                .build());

        MvcResult safeResult = mockMvc.perform(get("/api/v1/wellness/classes/safe")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn();
        String safeBody = safeResult.getResponse().getContentAsString();
        JsonNode safeJson = objectMapper.readTree(safeBody);
        log.info("Step 4 PASSED: Safe classes filtered:\n{}", safeBody);

        // Verify avoid contains HIIT session and eligible contains Yoga session
        boolean foundHiitInAvoid = false;
        for (JsonNode avoidItem : safeJson.get("avoid")) {
            if (avoidItem.get("session").get("id").asLong() == hiitSession.getId()) {
                foundHiitInAvoid = true;
                assertEquals("BLOCKED", avoidItem.get("verdict").asText());
                assertTrue(avoidItem.get("humanReasons").toString().contains("high-impact movement"));
            }
        }
        assertTrue(foundHiitInAvoid, "HIIT session must be categorized under 'avoid'");

        boolean foundYogaInEligible = false;
        for (JsonNode eligibleItem : safeJson.get("eligible")) {
            if (eligibleItem.get("session").get("id").asLong() == yogaSession.getId()) {
                foundYogaInEligible = true;
                assertNotNull(eligibleItem.get("verdict").asText());
            }
        }
        assertTrue(foundYogaInEligible, "Gentle Yoga session must be categorized under 'eligible'");

        // 5. Daily Nutrition Numbers (GET /api/v1/wellness/nutrition/numbers)
        // Hand calculations:
        // Weight: 70kg, Height: 175cm, Age: 28, Sex: MALE (+5)
        // BMR = 10*70 + 6.25*175 - 5*28 + 5 = 700 + 1093.75 - 140 + 5 = 1658.75 -> round HALF_UP = 1659 kcal
        // Planned sessions: 3 -> factor = 1.375
        // TDEE = 1658.75 * 1.375 = 2280.78125 -> round HALF_UP = 2281 kcal
        // Goal: LOSE_WEIGHT -> Deficit = min(0.15 * 2280.78125, 500) = min(342.117, 500) = 342.117 kcal
        // Target = 2280.78125 - 342.1171875 = 1938.664 -> round HALF_UP = 1939 kcal
        // Floor check: Male floor is 1500; 1939 > 1500 (floorApplied = false)
        MvcResult nutritionResult = mockMvc.perform(get("/api/v1/wellness/nutrition/numbers")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.bmr").value(1659))
                .andExpect(jsonPath("$.tdee").value(2281))
                .andExpect(jsonPath("$.targetCalories").value(1939))
                .andExpect(jsonPath("$.floorApplied").value(false))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty())
                .andReturn();
        log.info("Step 5 PASSED: Nutrition numbers verified against hand calculation:\n{}",
                nutritionResult.getResponse().getContentAsString());

        // 6. Direct SQL check on member_health_profiles table
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        String hexCipher = jdbcTemplate.queryForObject(
                "SELECT HEX(LEFT(data_encrypted, 40)) FROM member_health_profiles WHERE member_id = ?",
                String.class, member.getId());
        assertNotNull(hexCipher);
        log.info("Step 6: Raw DB HEX ciphertext snippet: {}", hexCipher);

        byte[] rawBytes = jdbcTemplate.queryForObject(
                "SELECT data_encrypted FROM member_health_profiles WHERE member_id = ?",
                (rs, rowNum) -> rs.getBytes("data_encrypted"), member.getId());
        String rawStr = new String(rawBytes);
        assertFalse(rawStr.toLowerCase().contains("knee"));
        assertFalse(rawStr.toLowerCase().contains("moderate"));
        assertFalse(rawStr.toLowerCase().contains("acl"));
        assertFalse(rawStr.toLowerCase().contains("surgery"));
        assertFalse(rawStr.contains("175.0"));
        assertFalse(rawStr.contains("70.0"));
        log.info("Step 6 PASSED: Ciphertext verified - zero plaintext leakage in database");

        // 7. Submit Feedback with pain reported (POST /api/v1/wellness/feedback)
        Booking booking = bookingRepository.save(Booking.builder()
                .sessionId(yogaSession.getId())
                .memberId(member.getId())
                .status(BookingStatus.ATTENDED)
                .consumedFrom(ConsumedFrom.FREE_SESSION)
                .bookedAt(clock.instant().minus(Duration.ofDays(2)))
                .version(0)
                .createdAt(clock.instant().minus(Duration.ofDays(2)))
                .build());

        attendanceRecordRepository.save(AttendanceRecord.builder()
                .bookingId(booking.getId())
                .markedBy(member.getId())
                .markedAt(clock.instant().minus(Duration.ofDays(1)))
                .status(AttendanceStatus.ATTENDED)
                .createdAt(clock.instant().minus(Duration.ofDays(1)))
                .build());

        ClassFeedbackRequest fbReq = new ClassFeedbackRequest(
                3,
                true,
                InjuryArea.KNEE,
                "Sharp twinge in the patella during warrior pose"
        );
        mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/feedback")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fbReq)))
                .andExpect(status().isOk());

        // Verify restriction proposal generated
        List<RestrictionProposal> proposals = proposalRepository.findAllByMemberIdAndStatusOrderByCreatedAtDesc(member.getId(), "PENDING");
        assertFalse(proposals.isEmpty(), "Restriction proposal must be generated when pain is reported");
        assertEquals("KNEE", proposals.get(0).getArea());
        log.info("Step 7 PASSED: Pain feedback recorded, proposal generated: id={}, area={}, severity={}",
                proposals.get(0).getId(), proposals.get(0).getArea(), proposals.get(0).getSuggestedSeverity());

        // Verify outbox row feedback.pain_reported without member identity
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        OutboxEvent painEvent = outboxList.stream()
                .filter(e -> "feedback.pain_reported".equals(e.getEventType()))
                .reduce((first, second) -> second)
                .orElse(null);
        assertNotNull(painEvent);
        assertEquals("SESSION", painEvent.getAggregateType());
        assertEquals(booking.getSessionId(), painEvent.getAggregateId());
        assertFalse(painEvent.getPayload().contains("walkthrough.member"), "Outbox payload must not contain member identity");
        log.info("Step 7 PASSED: Anonymous outbox event feedback.pain_reported confirmed: {}", painEvent.getPayload());

        // 8. Member exports data (GET /api/v1/wellness/export)
        MvcResult exportResult = mockMvc.perform(get("/api/v1/wellness/export")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile").isNotEmpty())
                .andExpect(jsonPath("$.consentHistory").isArray())
                .andExpect(jsonPath("$.accessLogs").isArray())
                .andReturn();
        String exportJson = exportResult.getResponse().getContentAsString();
        log.info("Step 8 PASSED: Complete DPDP export dataset generated:\n{}", exportJson);

        // 9. Member deletes health data (DELETE /api/v1/wellness/data)
        DeleteHealthDataRequest delReq = new DeleteHealthDataRequest("DELETE MY HEALTH DATA");
        mockMvc.perform(delete("/api/v1/wellness/data")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delReq)))
                .andExpect(status().isOk());

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_health_profiles WHERE member_id = ?", Integer.class, member.getId());
        assertEquals(0, profileCount, "Health profile must be hard deleted");

        // Profile returns 404
        mockMvc.perform(get("/api/v1/wellness/profile")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());

        // Export returns null profile, but consent history is preserved
        MvcResult exportAfterDelete = mockMvc.perform(get("/api/v1/wellness/export")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode exportAfterNode = objectMapper.readTree(exportAfterDelete.getResponse().getContentAsString());
        assertTrue(exportAfterNode.get("profile") == null || exportAfterNode.get("profile").isNull(),
                "Profile must be null in export after deletion");
        assertTrue(exportAfterNode.get("consentHistory").size() >= 2, "Consent audit history preserved");
        log.info("Step 9 PASSED: Health data hard deleted, profile 404 confirmed, consent history preserved for compliance.");
        log.info("========== STAGE 7 LIVE WALKTHROUGH COMPLETE ==========");
    }
}
