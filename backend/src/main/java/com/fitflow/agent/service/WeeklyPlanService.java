package com.fitflow.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fitflow.agent.entity.PendingAction;
import com.fitflow.agent.entity.WeeklyPlan;
import com.fitflow.agent.repository.PendingActionRepository;
import com.fitflow.agent.repository.WeeklyPlanRepository;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.wellness.WellnessProfileService;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.SafeClassesResponse;
import com.fitflow.wellness.dto.SafeSessionItem;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.DayAvailability;
import com.fitflow.wellness.model.ExperienceLevel;
import com.fitflow.wellness.model.Goal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyPlanService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    public static volatile boolean sanityBreakSkipCandidateSubsetCheck = false;

    private final MembershipService membershipService;
    private final WellnessProfileService wellnessProfileService;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final PendingActionRepository pendingActionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final Map<String, List<String>> goalCategoryPreferences = new HashMap<>();

    public record CandidatePlanResult(
            LocalDate weekStart,
            int targetCount,
            int entitlementRemaining,
            List<SessionDetailResponse> candidates,
            List<String> ruleExplanations
    ) {}

    @jakarta.annotation.PostConstruct
    public void initGoalCategories() {
        try {
            ClassPathResource res = new ClassPathResource("agents/goal-categories.yml");
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            try (InputStream is = res.getInputStream()) {
                Map<String, Object> root = yamlMapper.readValue(is, Map.class);
                Map<String, List<String>> goalsMap = (Map<String, List<String>>) root.get("goals");
                if (goalsMap != null) {
                    goalCategoryPreferences.putAll(goalsMap);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load goal-categories.yml, using defaults: {}", e.getMessage());
            goalCategoryPreferences.put("LOSE_WEIGHT", List.of("HIIT", "CARDIO", "DANCE"));
            goalCategoryPreferences.put("BUILD_STRENGTH", List.of("STRENGTH"));
            goalCategoryPreferences.put("IMPROVE_FLEXIBILITY", List.of("YOGA", "PILATES", "MOBILITY"));
            goalCategoryPreferences.put("ENDURANCE", List.of("CARDIO", "HIIT"));
            goalCategoryPreferences.put("GENERAL_FITNESS", List.of("ANY"));
            goalCategoryPreferences.put("REHAB_FRIENDLY", List.of("MOBILITY", "PILATES", "YOGA"));
        }
    }

    @Transactional(readOnly = true)
    public CandidatePlanResult buildWeeklyPlanCandidates(Long memberId, LocalDate requestedWeekStart) {
        LocalDate weekStart = requestedWeekStart != null ? requestedWeekStart :
                LocalDate.now(clock).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        List<String> explanations = new ArrayList<>();

        // 1. Entitlement remaining
        int entitlementRemaining = Integer.MAX_VALUE;
        try {
            MemberMembershipSummaryResponse memSummary = membershipService.getMemberMembership(memberId);
            int total = memSummary.freeSessionsRemaining();
            if (memSummary.paid() != null && memSummary.paid().sessionsRemaining() != null) {
                total += memSummary.paid().sessionsRemaining();
            }
            entitlementRemaining = total;
        } catch (Exception ignored) {}

        if (entitlementRemaining <= 0) {
            explanations.add("You have 0 sessions remaining on your active membership.");
            return new CandidatePlanResult(weekStart, 0, 0, List.of(), explanations);
        }

        // 2. Member profile (if consent exists)
        FitnessProfile profile = null;
        try {
            profile = wellnessProfileService.getProfile(memberId).profile();
        } catch (Exception ignored) {}

        int sessionsPlanned = (profile != null && profile.sessionsPerWeekPlanned() != null && profile.sessionsPerWeekPlanned() > 0)
                ? profile.sessionsPerWeekPlanned() : 3;

        int targetCount = Math.min(sessionsPlanned, entitlementRemaining);
        explanations.add(String.format("Targeting %d sessions for the week starting %s based on your weekly goals and plan entitlement.", targetCount, weekStart));

        ExperienceLevel expLevel = profile != null && profile.experienceLevel() != null ? profile.experienceLevel() : ExperienceLevel.BEGINNER;
        int maxHighIntensity = (expLevel == ExperienceLevel.BEGINNER) ? 2 : 3;

        // 3. Eligible sessions from safety filter
        Instant from = weekStart.atStartOfDay(ZONE).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(ZONE).toInstant();

        List<SessionDetailResponse> safeSessions = new ArrayList<>();
        try {
            SafeClassesResponse safeResponse = wellnessProfileService.getSafeClasses(memberId, from, to, 0, 100);
            for (SafeSessionItem item : safeResponse.eligible()) {
                safeSessions.add(item.session());
            }
        } catch (Exception e) {
            log.warn("Could not query safe classes for member {}: {}", memberId, e.getMessage());
        }

        // 4. Filter by profile availability if available
        List<DayAvailability> availability = (profile != null && profile.availability() != null) ? profile.availability() : List.of();
        List<SessionDetailResponse> availableSessions = safeSessions.stream()
                .filter(s -> matchesAvailability(s, availability))
                .toList();

        // 5. Filter out sessions that conflict with existing bookings
        List<SessionDetailResponse> nonConflicting = availableSessions.stream()
                .filter(s -> bookingRepository.findOverlappingConfirmedBookings(memberId, s.startsAt(), s.endsAt()).isEmpty())
                .toList();

        // 6. Sort candidate sessions by goal preference tier, then startsAt, then id
        Set<String> preferredCategories = new HashSet<>();
        if (profile != null && profile.goals() != null) {
            for (Goal g : profile.goals()) {
                List<String> cats = goalCategoryPreferences.get(g.name());
                if (cats != null) {
                    preferredCategories.addAll(cats);
                }
            }
        }

        List<SessionDetailResponse> sorted = nonConflicting.stream()
                .sorted(Comparator.comparing((SessionDetailResponse s) -> isPreferredCategory(s.category(), preferredCategories) ? 0 : 1)
                        .thenComparing(SessionDetailResponse::startsAt)
                        .thenComparing(SessionDetailResponse::id))
                .toList();

        // 7. Greedy selection enforcing Recovery and Variety rules
        List<SessionDetailResponse> candidates = new ArrayList<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<LocalDate, String> dailyIntensities = new HashMap<>();
        int highIntensityCount = 0;

        for (SessionDetailResponse session : sorted) {
            if (candidates.size() >= targetCount) {
                break;
            }

            LocalDate sessionDate = session.startsAt().atZone(ZONE).toLocalDate();

            // Variety rule: at most 2 of the same category
            String cat = session.category() != null ? session.category().toUpperCase(Locale.ROOT) : "GENERAL";
            if (categoryCounts.getOrDefault(cat, 0) >= 2) {
                continue;
            }

            // No two sessions on same time window
            boolean overlapsExisting = candidates.stream().anyMatch(c ->
                    schedulesOverlap(c.startsAt(), c.endsAt(), session.startsAt(), session.endsAt()));
            if (overlapsExisting) {
                continue;
            }

            // Recovery rule 1: max HIGH intensity sessions
            boolean isHigh = "HIGH".equalsIgnoreCase(session.intensity());
            if (isHigh && highIntensityCount >= maxHighIntensity) {
                continue;
            }

            // Recovery rule 2: HIGH intensity never on more than 2 consecutive days
            if (isHigh && wouldViolateConsecutiveHigh(dailyIntensities, sessionDate)) {
                continue;
            }

            // Accept session
            candidates.add(session);
            categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);
            if (isHigh) {
                highIntensityCount++;
                dailyIntensities.put(sessionDate, "HIGH");
            } else {
                dailyIntensities.putIfAbsent(sessionDate, session.intensity());
            }
        }

        if (highIntensityCount > 0) {
            explanations.add(String.format("Scheduled %d high-intensity workout(s), respecting recovery limits for %s level.",
                    highIntensityCount, expLevel));
        }
        if (!categoryCounts.isEmpty()) {
            explanations.add("Enforced category variety rule (maximum 2 sessions per workout category).");
        }

        return new CandidatePlanResult(weekStart, targetCount, entitlementRemaining, candidates, explanations);
    }

    @Transactional("postgresTransactionManager")
    public Long proposeWeeklyPlan(Long memberId, LocalDate weekStart, List<Long> sessionIds, Long conversationId) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            throw ApiException.badRequest("sessionIds cannot be empty");
        }

        // Verify sessionIds are a subset of current candidates
        CandidatePlanResult plannerResult = buildWeeklyPlanCandidates(memberId, weekStart);
        Set<Long> candidateIds = plannerResult.candidates().stream().map(SessionDetailResponse::id).collect(Collectors.toSet());

        if (!sanityBreakSkipCandidateSubsetCheck) {
            for (Long sid : sessionIds) {
                if (!candidateIds.contains(sid)) {
                    throw ApiException.badRequest("Session ID " + sid + " is not in the planner's candidate list for week starting " + weekStart);
                }
            }
        }

        Map<String, Object> planData = Map.of(
                "weekStart", weekStart.toString(),
                "sessionIds", sessionIds,
                "candidateCount", candidateIds.size(),
                "proposedCount", sessionIds.size()
        );

        String planJson;
        try {
            planJson = objectMapper.writeValueAsString(planData);
        } catch (Exception e) {
            throw ApiException.internalError("Failed to serialize weekly plan");
        }

        WeeklyPlan plan = WeeklyPlan.builder()
                .memberId(memberId)
                .weekStart(weekStart)
                .status("PROPOSED")
                .plan(planJson)
                .createdAt(clock.instant())
                .build();
        plan = weeklyPlanRepository.save(plan);

        return plan.getId();
    }

    @Transactional
    public Map<String, Object> executeWeeklyPlanConfirmation(Long planId, Long memberId, Long actionId) {
        WeeklyPlan plan = weeklyPlanRepository.findById(planId)
                .orElseThrow(() -> ApiException.notFound("Weekly plan not found"));

        if (!plan.getMemberId().equals(memberId)) {
            throw ApiException.forbidden("Cannot confirm plan belonging to another member");
        }

        Map<String, Object> planData;
        try {
            planData = objectMapper.readValue(plan.getPlan(), Map.class);
        } catch (Exception e) {
            throw ApiException.internalError("Invalid plan JSON");
        }

        List<Number> rawSessionIds = (List<Number>) planData.get("sessionIds");
        List<Long> sessionIds = rawSessionIds.stream().map(Number::longValue).toList();

        List<Map<String, Object>> bookingResults = new ArrayList<>();
        int confirmedCount = 0;
        int failedCount = 0;

        for (Long sid : sessionIds) {
            String idempotencyKey = String.format("agent-%d-%d", actionId, sid);
            try {
                var res = bookingService.createBooking(new CreateBookingRequest(sid), idempotencyKey, memberId);
                confirmedCount++;
                bookingResults.add(Map.of("sessionId", sid, "status", "CONFIRMED", "body", res.body()));
            } catch (Exception e) {
                failedCount++;
                bookingResults.add(Map.of("sessionId", sid, "status", "FAILED", "reason", e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        }

        String finalStatus;
        if (confirmedCount == sessionIds.size()) {
            finalStatus = "BOOKED";
        } else if (confirmedCount > 0) {
            finalStatus = "PARTIAL";
        } else {
            finalStatus = "FAILED";
        }

        plan.setStatus(finalStatus);
        try {
            plan.setResultsJson(objectMapper.writeValueAsString(bookingResults));
        } catch (Exception ignored) {}
        weeklyPlanRepository.save(plan);

        return Map.of(
                "planId", planId,
                "status", finalStatus,
                "confirmedCount", confirmedCount,
                "failedCount", failedCount,
                "results", bookingResults
        );
    }

    private boolean matchesAvailability(SessionDetailResponse session, List<DayAvailability> availability) {
        if (availability == null || availability.isEmpty()) {
            return true;
        }
        ZonedDateTime zdt = session.startsAt().atZone(ZONE);
        int day = zdt.getDayOfWeek().getValue();
        LocalTime sessionStart = zdt.toLocalTime();
        LocalTime sessionEnd = session.endsAt().atZone(ZONE).toLocalTime();

        return availability.stream().anyMatch(slot -> {
            if (slot.dayOfWeek() != day) return false;
            LocalTime slotStart = LocalTime.parse(slot.from());
            LocalTime slotEnd = LocalTime.parse(slot.to());
            return !sessionStart.isBefore(slotStart) && !sessionEnd.isAfter(slotEnd);
        });
    }

    private boolean isPreferredCategory(String category, Set<String> preferred) {
        if (preferred.contains("ANY")) return true;
        return category != null && preferred.contains(category.toUpperCase(Locale.ROOT));
    }

    private boolean schedulesOverlap(Instant start1, Instant end1, Instant start2, Instant end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private boolean wouldViolateConsecutiveHigh(Map<LocalDate, String> dailyIntensities, LocalDate candidateDate) {
        LocalDate prev1 = candidateDate.minusDays(1);
        LocalDate prev2 = candidateDate.minusDays(2);
        LocalDate next1 = candidateDate.plusDays(1);
        LocalDate next2 = candidateDate.plusDays(2);

        // Pattern 1: prev2 (HIGH) + prev1 (HIGH) + candidate (HIGH) -> 3 consecutive!
        if ("HIGH".equalsIgnoreCase(dailyIntensities.get(prev1)) && "HIGH".equalsIgnoreCase(dailyIntensities.get(prev2))) {
            return true;
        }
        // Pattern 2: prev1 (HIGH) + candidate (HIGH) + next1 (HIGH) -> 3 consecutive!
        if ("HIGH".equalsIgnoreCase(dailyIntensities.get(prev1)) && "HIGH".equalsIgnoreCase(dailyIntensities.get(next1))) {
            return true;
        }
        // Pattern 3: candidate (HIGH) + next1 (HIGH) + next2 (HIGH) -> 3 consecutive!
        if ("HIGH".equalsIgnoreCase(dailyIntensities.get(next1)) && "HIGH".equalsIgnoreCase(dailyIntensities.get(next2))) {
            return true;
        }
        return false;
    }
}
