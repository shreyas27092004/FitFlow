package com.fitflow.wellness;

import com.fitflow.booking.entity.AttendanceRecord;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.AttendanceStatus;
import com.fitflow.booking.repository.AttendanceRecordRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.security.EncryptionService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.wellness.dto.AcceptProposalRequest;
import com.fitflow.wellness.dto.ClassFeedbackRequest;
import com.fitflow.wellness.dto.FeedbackResponse;
import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.MissedSessionDto;
import com.fitflow.wellness.dto.RestrictionProposalDto;
import com.fitflow.wellness.dto.WeeklyProgressResponse;
import com.fitflow.wellness.entity.ClassFeedback;
import com.fitflow.wellness.entity.MemberHealthProfile;
import com.fitflow.wellness.entity.RestrictionProposal;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.InjuryArea;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.repository.ClassFeedbackRepository;
import com.fitflow.wellness.repository.MemberHealthProfileRepository;
import com.fitflow.wellness.repository.RestrictionProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WellnessFeedbackService {

    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");

    private final BookingRepository bookingRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClassSessionRepository sessionRepository;
    private final FitnessClassRepository fitnessClassRepository;
    private final ClassFeedbackRepository feedbackRepository;
    private final RestrictionProposalRepository proposalRepository;
    private final MemberHealthProfileRepository profileRepository;
    private final HealthDataCodec healthDataCodec;
    private final ConsentService consentService;
    private final CalorieService calorieService;
    private final EncryptionService encryptionService;
    private final OutboxService outboxService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WeeklyProgressResponse getWeeklyProgress(Long memberId, LocalDate weekStart) {
        if (weekStart == null) {
            weekStart = LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        }

        Instant weekStartInstant = weekStart.atStartOfDay(DISPLAY_ZONE).toInstant();
        Instant weekEndInstant = weekStart.plusDays(7).atStartOfDay(DISPLAY_ZONE).toInstant();

        List<Booking> allBookings = bookingRepository.findAllByMemberId(memberId);

        int bookedCount = 0;
        int attendedCount = 0;
        int noShowCount = 0;
        List<MissedSessionDto> missedSessions = new ArrayList<>();

        // Map session IDs to ClassSession
        Map<Long, ClassSession> sessionMap = new HashMap<>();
        Map<Long, FitnessClass> classMap = new HashMap<>();

        List<Booking> attendedBookingsInWeek = new ArrayList<>();

        for (Booking booking : allBookings) {
            ClassSession session = sessionMap.computeIfAbsent(booking.getSessionId(),
                    id -> sessionRepository.findById(id).orElse(null));
            if (session == null) {
                continue;
            }

            Instant sessionStartsAt = session.getStartsAt();
            if (!sessionStartsAt.isBefore(weekStartInstant) && sessionStartsAt.isBefore(weekEndInstant)) {
                bookedCount++;

                FitnessClass fc = classMap.computeIfAbsent(session.getClassId(),
                        id -> fitnessClassRepository.findById(id).orElse(null));
                String className = fc != null ? fc.getName() : "Class";

                Optional<AttendanceRecord> attendanceOpt = attendanceRecordRepository.findByBookingId(booking.getId());
                if (attendanceOpt.isPresent()) {
                    AttendanceStatus status = attendanceOpt.get().getStatus();
                    if (status == AttendanceStatus.ATTENDED) {
                        attendedCount++;
                        attendedBookingsInWeek.add(booking);
                    } else if (status == AttendanceStatus.NO_SHOW) {
                        noShowCount++;
                        missedSessions.add(new MissedSessionDto(session.getId(), session.getStartsAt(), className));
                    }
                } else if (session.getEndsAt().isBefore(clock.instant())) {
                    noShowCount++;
                    missedSessions.add(new MissedSessionDto(session.getId(), session.getStartsAt(), className));
                }
            }
        }

        // Streak calculation: consecutive ISO weeks ending at requested week with at least 1 attended session
        int streakWeeks = computeStreakWeeks(memberId, weekStart, attendedCount);

        // Kcal calculation: only if member has CALORIE_ESTIMATES consent AND profile with weight
        Integer estimatedKcalBurned = null;
        if (consentService.hasConsent(memberId, ConsentPurpose.CALORIE_ESTIMATES)) {
            Optional<MemberHealthProfile> profOpt = profileRepository.findByMemberId(memberId);
            if (profOpt.isPresent()) {
                FitnessProfile profile = healthDataCodec.decode(profOpt.get().getDataEncrypted());
                if (profile != null && profile.weightKg() != null) {
                    BigDecimal weightKg = profile.weightKg();
                    int totalKcal = 0;
                    for (Booking b : attendedBookingsInWeek) {
                        ClassSession s = sessionMap.get(b.getSessionId());
                        if (s != null) {
                            FitnessClass fc = classMap.get(s.getClassId());
                            if (fc != null && fc.getMetValue() != null && fc.getDurationMinutes() != null) {
                                totalKcal += calorieService.calculateBurn(fc.getMetValue(), weightKg, fc.getDurationMinutes());
                            }
                        }
                    }
                    estimatedKcalBurned = totalKcal;
                }
            }
        }

        return new WeeklyProgressResponse(
                bookedCount,
                attendedCount,
                noShowCount,
                streakWeeks,
                estimatedKcalBurned,
                missedSessions
        );
    }

    private int computeStreakWeeks(Long memberId, LocalDate targetWeekStart, int currentWeekAttendedCount) {
        if (currentWeekAttendedCount == 0) {
            return 0;
        }

        int streak = 1;
        LocalDate checkWeek = targetWeekStart.minusWeeks(1);

        while (true) {
            Instant start = checkWeek.atStartOfDay(DISPLAY_ZONE).toInstant();
            Instant end = checkWeek.plusDays(7).atStartOfDay(DISPLAY_ZONE).toInstant();

            boolean hasAttended = false;
            List<Booking> bookings = bookingRepository.findAllByMemberId(memberId);
            for (Booking b : bookings) {
                ClassSession session = sessionRepository.findById(b.getSessionId()).orElse(null);
                if (session != null && !session.getStartsAt().isBefore(start) && session.getStartsAt().isBefore(end)) {
                    Optional<AttendanceRecord> att = attendanceRecordRepository.findByBookingId(b.getId());
                    if (att.isPresent() && att.get().getStatus() == AttendanceStatus.ATTENDED) {
                        hasAttended = true;
                        break;
                    }
                }
            }

            if (hasAttended) {
                streak++;
                checkWeek = checkWeek.minusWeeks(1);
            } else {
                break;
            }
        }
        return streak;
    }

    @Transactional
    public FeedbackResponse submitFeedback(Long memberId, Long bookingId, ClassFeedbackRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found with id: " + bookingId));

        if (!booking.getMemberId().equals(memberId)) {
            throw ApiException.forbidden("Cannot submit feedback for another member's booking");
        }

        AttendanceRecord attendance = attendanceRecordRepository.findByBookingId(bookingId)
                .orElseThrow(() -> ApiException.conflict("Booking must be marked ATTENDED to submit feedback"));

        if (attendance.getStatus() != AttendanceStatus.ATTENDED) {
            throw ApiException.conflict("Booking must be marked ATTENDED to submit feedback");
        }

        if (feedbackRepository.findByBookingId(bookingId).isPresent()) {
            throw ApiException.conflict("Feedback has already been submitted for booking id: " + bookingId);
        }

        if (request.feltPain() && request.painArea() == null) {
            throw ApiException.badRequest("painArea is required when feltPain is true");
        }

        byte[] commentEnc = null;
        if (request.comment() != null && !request.comment().isBlank()) {
            commentEnc = encryptionService.encrypt(request.comment().getBytes(StandardCharsets.UTF_8));
        }

        ClassFeedback feedback = ClassFeedback.builder()
                .bookingId(bookingId)
                .memberId(memberId)
                .rating(request.rating())
                .feltPain(request.feltPain())
                .painAreaCode(request.painArea() != null ? request.painArea().name() : null)
                .commentEnc(commentEnc)
                .createdAt(clock.instant())
                .build();
        feedbackRepository.save(feedback);

        Long proposalId = null;
        String message = "Thank you for your feedback!";

        if (request.feltPain()) {
            // 1. Insert restriction_proposals
            RestrictionProposal proposal = RestrictionProposal.builder()
                    .memberId(memberId)
                    .area(request.painArea().name())
                    .suggestedSeverity("MILD")
                    .status("PENDING")
                    .createdAt(clock.instant())
                    .build();
            RestrictionProposal savedProposal = proposalRepository.save(proposal);
            proposalId = savedProposal.getId();

            // 2. Append event feedback.pain_reported {sessionId, classId, areaCode} with NO member id and NO name
            ClassSession session = sessionRepository.findById(booking.getSessionId())
                    .orElseThrow(() -> ApiException.notFound("Session not found"));
            FitnessClass fitnessClass = fitnessClassRepository.findById(session.getClassId())
                    .orElseThrow(() -> ApiException.notFound("Class not found"));

            outboxService.append(
                    "SESSION",
                    session.getId(),
                    "feedback.pain_reported",
                    Map.of(
                            "sessionId", session.getId(),
                            "classId", fitnessClass.getId(),
                            "areaCode", request.painArea().name()
                    )
            );

            message = "Would you like to add this to your profile?";
        }

        return new FeedbackResponse(proposalId, message);
    }

    @Transactional(readOnly = true)
    public List<RestrictionProposalDto> getProposals(Long memberId) {
        return proposalRepository.findAllByMemberIdAndStatusOrderByCreatedAtDesc(memberId, "PENDING")
                .stream()
                .map(this::toProposalDto)
                .toList();
    }

    @Transactional
    public RestrictionProposalDto acceptProposal(Long memberId, Long proposalId, AcceptProposalRequest request) {
        RestrictionProposal proposal = proposalRepository.findByIdAndMemberId(proposalId, memberId)
                .orElseThrow(() -> ApiException.notFound("Restriction proposal not found"));

        if (!"PENDING".equalsIgnoreCase(proposal.getStatus())) {
            throw ApiException.conflict("Proposal is not in PENDING status: " + proposal.getStatus());
        }

        InjuryArea targetArea = InjuryArea.valueOf(proposal.getArea().toUpperCase());
        InjurySeverity requestedSeverity = request.severity();

        // Update profile if profile exists
        Optional<MemberHealthProfile> profOpt = profileRepository.findByMemberId(memberId);
        if (profOpt.isPresent()) {
            MemberHealthProfile entity = profOpt.get();
            FitnessProfile current = healthDataCodec.decode(entity.getDataEncrypted());
            if (current != null) {
                List<InjuryDto> currentInjuries = current.injuries() != null
                        ? new ArrayList<>(current.injuries())
                        : new ArrayList<>();

                // Rule: adds or raises an injury; never lowers an existing severity automatically
                Optional<InjuryDto> existingInjuryOpt = currentInjuries.stream()
                        .filter(i -> i.area() == targetArea)
                        .findFirst();

                if (existingInjuryOpt.isPresent()) {
                    InjuryDto existing = existingInjuryOpt.get();
                    InjurySeverity resolvedSeverity = requestedSeverity.ordinal() >= existing.severity().ordinal()
                            ? requestedSeverity
                            : existing.severity();
                    currentInjuries.remove(existing);
                    currentInjuries.add(new InjuryDto(targetArea, resolvedSeverity));
                } else {
                    currentInjuries.add(new InjuryDto(targetArea, requestedSeverity));
                }

                FitnessProfile updated = new FitnessProfile(
                        current.experienceLevel(),
                        current.pastExperienceNote(),
                        current.goals(),
                        current.sessionsPerWeekPlanned(),
                        current.availability(),
                        currentInjuries,
                        current.conditions(),
                        current.parq(),
                        current.medicalClearanceAttested(),
                        current.heightCm(),
                        current.weightKg(),
                        current.sex(),
                        current.dietPreference(),
                        current.version()
                );
                entity.setDataEncrypted(healthDataCodec.encode(updated));
                entity.setUpdatedAt(clock.instant());
                profileRepository.save(entity);
            }
        }

        proposal.setStatus("ACCEPTED");
        proposal.setUpdatedAt(clock.instant());
        RestrictionProposal saved = proposalRepository.save(proposal);

        return toProposalDto(saved);
    }

    @Transactional
    public RestrictionProposalDto dismissProposal(Long memberId, Long proposalId) {
        RestrictionProposal proposal = proposalRepository.findByIdAndMemberId(proposalId, memberId)
                .orElseThrow(() -> ApiException.notFound("Restriction proposal not found"));

        if (!"PENDING".equalsIgnoreCase(proposal.getStatus())) {
            throw ApiException.conflict("Proposal is not in PENDING status: " + proposal.getStatus());
        }

        proposal.setStatus("DISMISSED");
        proposal.setUpdatedAt(clock.instant());
        RestrictionProposal saved = proposalRepository.save(proposal);

        return toProposalDto(saved);
    }

    private RestrictionProposalDto toProposalDto(RestrictionProposal p) {
        return new RestrictionProposalDto(
                p.getId(),
                InjuryArea.valueOf(p.getArea().toUpperCase()),
                InjurySeverity.valueOf(p.getSuggestedSeverity().toUpperCase()),
                p.getStatus(),
                p.getCreatedAt()
        );
    }
}
