package com.fitflow.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.session.dto.CancelSessionRequest;
import com.fitflow.session.dto.CreateSessionRequest;
import com.fitflow.session.dto.RecurrenceRuleDto;
import com.fitflow.session.dto.RescheduleSessionRequest;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.session.dto.TrainerSessionResponse;
import com.fitflow.session.dto.UtilizationReportResponse;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.trainer.dto.AvailabilitySlotDto;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassSessionService {

    private final ClassSessionRepository sessionRepository;
    private final FitnessClassRepository fitnessClassRepository;
    private final TrainerProfileRepository trainerProfileRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final OutboxService outboxService;
    private final SessionBookingHandler sessionBookingHandler;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${fitflow.display-timezone:Asia/Kolkata}")
    private String displayTimezone;

    @Transactional
    public List<SessionDetailResponse> createSessions(CreateSessionRequest request) {
        Instant now = clock.instant();
        if (!request.startsAt().isAfter(now)) {
            throw ApiException.badRequest("Session startsAt must be in the future");
        }

        FitnessClass fitnessClass = fitnessClassRepository.findById(request.classId())
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + request.classId()));

        if (!Boolean.TRUE.equals(fitnessClass.getActive())) {
            throw ApiException.conflict("Cannot schedule sessions for deactivated fitness class id: " + request.classId());
        }

        TrainerProfile trainerProfile = validateTrainerActiveAndGetProfile(request.trainerId());
        Long trainerRateSnapshot = trainerProfile.getRatePerSessionPaise() != null ? trainerProfile.getRatePerSessionPaise() : 0L;
        int capacity = request.capacity() != null ? request.capacity() : fitnessClass.getDefaultCapacity();
        ZoneId zoneId = ZoneId.of(displayTimezone);
        List<AvailabilitySlotDto> availabilitySlots = deserializeAvailability(trainerProfile.getAvailability());

        List<Occurrence> occurrences = generateOccurrences(
                request.startsAt(),
                fitnessClass.getDurationMinutes(),
                request.recurrence(),
                zoneId,
                now
        );

        if (occurrences.isEmpty()) {
            throw ApiException.badRequest("No future occurrences generated from recurrence rule");
        }
        if (occurrences.size() > 60) {
            throw ApiException.badRequest("Recurrence creates at most 60 sessions per call (requested: " + occurrences.size() + ")");
        }

        // Validate conflicts across all occurrences
        List<String> conflictDates = new ArrayList<>();
        for (Occurrence occ : occurrences) {
            boolean available = isTrainerAvailable(occ.startsAt, occ.endsAt, availabilitySlots, zoneId);
            boolean trainerOverlap = !sessionRepository.findOverlappingTrainerSessions(request.trainerId(), occ.startsAt, occ.endsAt, null).isEmpty();
            boolean roomOverlap = !sessionRepository.findOverlappingRoomSessions(request.room().trim(), occ.startsAt, occ.endsAt, null).isEmpty();

            if (!available || trainerOverlap || roomOverlap) {
                LocalDate dateInZone = occ.startsAt.atZone(zoneId).toLocalDate();
                conflictDates.add(dateInZone.toString());
            }
        }

        if (!conflictDates.isEmpty()) {
            throw ApiException.conflict("Scheduling conflicts detected on dates: " + conflictDates);
        }

        String seriesId = occurrences.size() > 1 ? UUID.randomUUID().toString() : null;
        List<ClassSession> sessionsToSave = new ArrayList<>();

        for (Occurrence occ : occurrences) {
            ClassSession session = ClassSession.builder()
                    .classId(fitnessClass.getId())
                    .trainerId(request.trainerId())
                    .startsAt(occ.startsAt)
                    .endsAt(occ.endsAt)
                    .capacity(capacity)
                    .bookedCount(0)
                    .room(request.room().trim())
                    .status(ClassSession.Status.SCHEDULED)
                    .seriesId(seriesId)
                    .trainerRateSnapshotPaise(trainerRateSnapshot)
                    .build();
            sessionsToSave.add(session);
        }

        List<ClassSession> savedSessions = sessionRepository.saveAll(sessionsToSave);
        log.info("Created {} sessions for class id={} trainer id={} seriesId={}",
                savedSessions.size(), fitnessClass.getId(), request.trainerId(), seriesId);

        User trainerUser = userRepository.findById(request.trainerId()).orElse(null);
        String trainerName = trainerUser != null ? trainerUser.getFullName() : "Trainer";

        return savedSessions.stream()
                .map(s -> toDetailResponse(s, fitnessClass, trainerName, null))
                .toList();
    }

    @Transactional
    public SessionDetailResponse rescheduleSession(Long id, RescheduleSessionRequest request) {
        Instant now = clock.instant();
        if (!request.startsAt().isAfter(now)) {
            throw ApiException.badRequest("Session startsAt must be in the future");
        }

        ClassSession session = sessionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + id));

        if (!session.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("Session version mismatch: expected "
                    + session.getVersion() + " but got " + request.version());
        }

        if (session.getStatus() == ClassSession.Status.CANCELLED) {
            throw ApiException.conflict("Cannot reschedule a cancelled session");
        }

        FitnessClass fitnessClass = fitnessClassRepository.findById(session.getClassId())
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + session.getClassId()));

        TrainerProfile trainerProfile = validateTrainerActiveAndGetProfile(session.getTrainerId());
        ZoneId zoneId = ZoneId.of(displayTimezone);
        List<AvailabilitySlotDto> availabilitySlots = deserializeAvailability(trainerProfile.getAvailability());

        Instant newStartsAt = request.startsAt();
        Instant newEndsAt = newStartsAt.plus(Duration.ofMinutes(fitnessClass.getDurationMinutes()));

        // Conflict checks
        boolean available = isTrainerAvailable(newStartsAt, newEndsAt, availabilitySlots, zoneId);
        if (!available) {
            throw ApiException.conflict("Trainer is not available at new time: " + newStartsAt.atZone(zoneId));
        }

        boolean trainerOverlap = !sessionRepository.findOverlappingTrainerSessions(session.getTrainerId(), newStartsAt, newEndsAt, id).isEmpty();
        if (trainerOverlap) {
            throw ApiException.conflict("Trainer has another overlapping session at new time");
        }

        boolean roomOverlap = !sessionRepository.findOverlappingRoomSessions(session.getRoom(), newStartsAt, newEndsAt, id).isEmpty();
        if (roomOverlap) {
            throw ApiException.conflict("Room '" + session.getRoom() + "' has another overlapping session at new time");
        }

        Instant oldStartsAt = session.getStartsAt();
        session.setStartsAt(newStartsAt);
        session.setEndsAt(newEndsAt);

        ClassSession saved = sessionRepository.saveAndFlush(session);
        log.info("Session id={} rescheduled from {} to {}", id, oldStartsAt, newStartsAt);

        // Emit session.rescheduled
        Map<String, Object> payload = Map.of(
                "sessionId", id,
                "oldStartsAt", oldStartsAt.toString(),
                "newStartsAt", newStartsAt.toString(),
                "seriesId", session.getSeriesId() != null ? session.getSeriesId() : ""
        );
        outboxService.append("session", id, "session.rescheduled", payload);

        User trainerUser = userRepository.findById(session.getTrainerId()).orElse(null);
        String trainerName = trainerUser != null ? trainerUser.getFullName() : "Trainer";

        return toDetailResponse(saved, fitnessClass, trainerName, null);
    }

    @Transactional
    public SessionDetailResponse cancelSession(Long id, CancelSessionRequest request) {
        ClassSession session = sessionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + id));

        if (session.getStatus() == ClassSession.Status.CANCELLED) {
            throw ApiException.conflict("Session is already cancelled");
        }

        session.setStatus(ClassSession.Status.CANCELLED);
        ClassSession saved = sessionRepository.saveAndFlush(session);
        log.info("Session id={} cancelled. Reason: {}", id, request.reason());

        // Cancel bookings and restore entitlements in the same transaction
        sessionBookingHandler.cancelConfirmedBookingsForSession(id, request.reason());

        // Emit session.cancelled
        Map<String, Object> payload = Map.of(
                "sessionId", id,
                "reason", request.reason()
        );
        outboxService.append("session", id, "session.cancelled", payload);

        FitnessClass fitnessClass = fitnessClassRepository.findById(session.getClassId()).orElse(null);
        User trainerUser = userRepository.findById(session.getTrainerId()).orElse(null);
        String trainerName = trainerUser != null ? trainerUser.getFullName() : "Trainer";

        return toDetailResponse(saved, fitnessClass, trainerName, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionDetailResponse> getSessions(
            Instant from,
            Instant to,
            ClassCategory category,
            ClassIntensity intensity,
            Long trainerId,
            Boolean onlyAvailable,
            int page,
            int size,
            Long memberId) {

        Instant now = clock.instant();
        Instant effectiveFrom = from != null ? from : now;
        Instant effectiveTo = to != null ? to : effectiveFrom.plus(Duration.ofDays(14));

        long daysBetween = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
        if (daysBetween > 62) {
            throw ApiException.badRequest("Sessions date range cannot exceed 62 days");
        }

        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by("startsAt").ascending());

        Specification<ClassSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), effectiveFrom));
            predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), effectiveTo));
            predicates.add(cb.notEqual(root.get("status"), ClassSession.Status.CANCELLED));

            if (trainerId != null) {
                predicates.add(cb.equal(root.get("trainerId"), trainerId));
            }

            if (Boolean.TRUE.equals(onlyAvailable)) {
                predicates.add(cb.lessThan(root.get("bookedCount"), root.get("capacity")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ClassSession> sessionPage = sessionRepository.findAll(spec, pageable);
        List<ClassSession> content = sessionPage.getContent();

        // Batch fetch fitness classes and trainers
        List<Long> classIds = content.stream().map(ClassSession::getClassId).distinct().toList();
        Map<Long, FitnessClass> classMap = fitnessClassRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(FitnessClass::getId, fc -> fc));

        List<Long> trainerIds = content.stream().map(ClassSession::getTrainerId).distinct().toList();
        Map<Long, User> trainerMap = userRepository.findAllById(trainerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Filter by category or intensity if requested
        List<ClassSession> filtered = content.stream().filter(s -> {
            FitnessClass fc = classMap.get(s.getClassId());
            if (fc == null) return false;
            if (category != null && !category.name().equalsIgnoreCase(fc.getCategory())) return false;
            if (intensity != null && !intensity.equals(fc.getIntensity())) return false;
            return true;
        }).toList();

        List<Long> sessionIds = filtered.stream().map(ClassSession::getId).toList();
        Map<Long, String> bookingStatuses = memberId != null
                ? sessionBookingHandler.getMemberBookingStatuses(sessionIds, memberId)
                : Collections.emptyMap();

        List<SessionDetailResponse> dtos = filtered.stream().map(s -> {
            FitnessClass fc = classMap.get(s.getClassId());
            User tr = trainerMap.get(s.getTrainerId());
            String trName = tr != null ? tr.getFullName() : "Trainer";
            String myStatus = bookingStatuses.get(s.getId());
            return toDetailResponse(s, fc, trName, myStatus);
        }).toList();

        return new PageResponse<>(
                dtos,
                sessionPage.getNumber(),
                sessionPage.getSize(),
                sessionPage.getTotalElements(),
                sessionPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionById(Long id, Long memberId) {
        ClassSession session = sessionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + id));

        FitnessClass fitnessClass = fitnessClassRepository.findById(session.getClassId()).orElse(null);
        User trainerUser = userRepository.findById(session.getTrainerId()).orElse(null);
        String trainerName = trainerUser != null ? trainerUser.getFullName() : "Trainer";
        String myStatus = memberId != null ? sessionBookingHandler.getMemberBookingStatus(id, memberId) : null;

        return toDetailResponse(session, fitnessClass, trainerName, myStatus);
    }

    @Transactional(readOnly = true)
    public List<TrainerSessionResponse> getTrainerSessions(Long trainerId, Instant from, Instant to) {
        Instant now = clock.instant();
        Instant effectiveFrom = from != null ? from : now.minus(Duration.ofDays(7));
        Instant effectiveTo = to != null ? to : now.plus(Duration.ofDays(14));

        List<ClassSession> sessions = sessionRepository.findByTrainerIdAndStartsAtBetweenOrderByStartsAtAsc(
                trainerId, effectiveFrom, effectiveTo
        );

        List<Long> classIds = sessions.stream().map(ClassSession::getClassId).distinct().toList();
        Map<Long, FitnessClass> classMap = fitnessClassRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(FitnessClass::getId, fc -> fc));

        return sessions.stream().map(s -> {
            FitnessClass fc = classMap.get(s.getClassId());
            String className = fc != null ? fc.getName() : "Class";

            List<SessionBookingHandler.AttendeeSummary> attendees = sessionBookingHandler.getAttendeesForSession(s.getId());
            int confirmedCount = (int) attendees.stream()
                    .filter(a -> "CONFIRMED".equalsIgnoreCase(a.status()) || "ATTENDED".equalsIgnoreCase(a.status()))
                    .count();

            List<TrainerSessionResponse.TrainerAttendeeDto> attendeeDtos = attendees.stream()
                    .map(a -> new TrainerSessionResponse.TrainerAttendeeDto(a.bookingId(), a.firstName(), a.status()))
                    .toList();

            return new TrainerSessionResponse(
                    s.getId(),
                    s.getClassId(),
                    className,
                    s.getStartsAt(),
                    s.getEndsAt(),
                    s.getRoom(),
                    s.getCapacity(),
                    s.getBookedCount(),
                    s.getStatus(),
                    s.getTrainerRateSnapshotPaise(),
                    confirmedCount,
                    attendeeDtos
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public UtilizationReportResponse getUtilizationReport(Instant from, Instant to) {
        Instant now = clock.instant();
        Instant effectiveFrom = from != null ? from : now.minus(Duration.ofDays(30));
        Instant effectiveTo = to != null ? to : now.plus(Duration.ofDays(14));

        long daysBetween = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
        if (daysBetween > 92) {
            throw ApiException.badRequest("Utilization report date range cannot exceed 92 days");
        }

        List<ClassSession> sessions = sessionRepository.findByStartsAtBetween(effectiveFrom, effectiveTo).stream()
                .filter(s -> s.getStatus() != ClassSession.Status.CANCELLED)
                .toList();

        List<Long> classIds = sessions.stream().map(ClassSession::getClassId).distinct().toList();
        Map<Long, FitnessClass> classMap = fitnessClassRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(FitnessClass::getId, fc -> fc));

        // Group by class
        Map<Long, List<ClassSession>> byClass = sessions.stream()
                .collect(Collectors.groupingBy(ClassSession::getClassId));

        List<UtilizationReportResponse.ClassUtilizationDto> classDtos = new ArrayList<>();
        for (Map.Entry<Long, List<ClassSession>> entry : byClass.entrySet()) {
            Long cId = entry.getKey();
            List<ClassSession> cSessions = entry.getValue();
            FitnessClass fc = classMap.get(cId);
            String className = fc != null ? fc.getName() : "Class #" + cId;

            long totalSessions = cSessions.size();
            long totalCapacity = cSessions.stream().mapToLong(ClassSession::getCapacity).sum();
            long totalBooked = cSessions.stream().mapToLong(ClassSession::getBookedCount).sum();
            double pct = totalCapacity > 0 ? Math.round((totalBooked * 1000.0) / totalCapacity) / 10.0 : 0.0;

            classDtos.add(new UtilizationReportResponse.ClassUtilizationDto(
                    cId, className, totalSessions, totalCapacity, totalBooked, pct
            ));
        }

        // Group by week
        ZoneId zoneId = ZoneId.of(displayTimezone);
        Map<String, List<ClassSession>> byWeek = sessions.stream().collect(Collectors.groupingBy(s -> {
            ZonedDateTime zdt = s.getStartsAt().atZone(zoneId);
            int year = zdt.get(IsoFields.WEEK_BASED_YEAR);
            int week = zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return String.format("%d-W%02d", year, week);
        }, LinkedHashMap::new, Collectors.toList()));

        List<UtilizationReportResponse.WeeklyUtilizationDto> weekDtos = new ArrayList<>();
        for (Map.Entry<String, List<ClassSession>> entry : byWeek.entrySet()) {
            String weekLabel = entry.getKey();
            List<ClassSession> wSessions = entry.getValue();

            long totalSessions = wSessions.size();
            long totalCapacity = wSessions.stream().mapToLong(ClassSession::getCapacity).sum();
            long totalBooked = wSessions.stream().mapToLong(ClassSession::getBookedCount).sum();
            double pct = totalCapacity > 0 ? Math.round((totalBooked * 1000.0) / totalCapacity) / 10.0 : 0.0;

            weekDtos.add(new UtilizationReportResponse.WeeklyUtilizationDto(
                    weekLabel, totalSessions, totalCapacity, totalBooked, pct
            ));
        }

        return new UtilizationReportResponse(classDtos, weekDtos);
    }

    private TrainerProfile validateTrainerActiveAndGetProfile(Long trainerId) {
        User user = userRepository.findById(trainerId)
                .orElseThrow(() -> ApiException.notFound("Trainer not found with id: " + trainerId));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.conflict("Trainer account is not active");
        }

        boolean hasTrainerRole = userRoleRepository.existsByIdUserIdAndIdRole(trainerId, UserRole.Role.TRAINER);
        if (!hasTrainerRole) {
            throw ApiException.conflict("User with id " + trainerId + " does not hold role TRAINER");
        }

        TrainerProfile profile = trainerProfileRepository.findById(trainerId)
                .orElseThrow(() -> ApiException.notFound("Trainer profile not found for user id: " + trainerId));

        if (!Boolean.TRUE.equals(profile.getActive())) {
            throw ApiException.conflict("Trainer profile is marked inactive");
        }

        return profile;
    }

    private boolean isTrainerAvailable(
            Instant startsAt,
            Instant endsAt,
            List<AvailabilitySlotDto> slots,
            ZoneId zoneId) {

        if (slots == null || slots.isEmpty()) {
            return false;
        }

        ZonedDateTime startZdt = startsAt.atZone(zoneId);
        ZonedDateTime endZdt = endsAt.atZone(zoneId);

        int dayOfWeek = startZdt.getDayOfWeek().getValue(); // 1 = Monday .. 7 = Sunday
        LocalTime sessionStart = startZdt.toLocalTime();
        LocalTime sessionEnd = endZdt.toLocalTime();

        for (AvailabilitySlotDto slot : slots) {
            if (slot.dayOfWeek() == dayOfWeek) {
                LocalTime slotStart = LocalTime.parse(slot.start());
                LocalTime slotEnd = LocalTime.parse(slot.end());

                if (!sessionStart.isBefore(slotStart) && !sessionEnd.isAfter(slotEnd)) {
                    return true;
                }
            }
        }
        return false;
    }

    private record Occurrence(Instant startsAt, Instant endsAt) {}

    private List<Occurrence> generateOccurrences(
            Instant startsAt,
            int durationMinutes,
            RecurrenceRuleDto recurrence,
            ZoneId zoneId,
            Instant now) {

        List<Occurrence> list = new ArrayList<>();
        Duration duration = Duration.ofMinutes(durationMinutes);

        if (recurrence == null) {
            list.add(new Occurrence(startsAt, startsAt.plus(duration)));
            return list;
        }

        ZonedDateTime baseZdt = startsAt.atZone(zoneId);
        LocalTime timeOfDay = baseZdt.toLocalTime();
        LocalDate startDate = baseZdt.toLocalDate();
        LocalDate untilDate = recurrence.until();

        if (untilDate.isBefore(startDate)) {
            throw ApiException.badRequest("Recurrence until date cannot be before startsAt date");
        }

        LocalDate curr = startDate;
        while (!curr.isAfter(untilDate)) {
            int dow = curr.getDayOfWeek().getValue();
            if (recurrence.daysOfWeek().contains(dow)) {
                ZonedDateTime occZdt = curr.atTime(timeOfDay).atZone(zoneId);
                Instant occStart = occZdt.toInstant();
                if (occStart.isAfter(now)) {
                    list.add(new Occurrence(occStart, occStart.plus(duration)));
                }
            }
            curr = curr.plusDays(1);
        }

        return list;
    }

    private List<AvailabilitySlotDto> deserializeAvailability(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private SessionDetailResponse toDetailResponse(
            ClassSession session,
            FitnessClass fitnessClass,
            String trainerName,
            String myBookingStatus) {

        String className = fitnessClass != null ? fitnessClass.getName() : "Class #" + session.getClassId();
        String category = fitnessClass != null ? fitnessClass.getCategory() : null;
        Integer durationMinutes = fitnessClass != null ? fitnessClass.getDurationMinutes() : null;
        String intensity = fitnessClass != null && fitnessClass.getIntensity() != null ? fitnessClass.getIntensity().name() : null;
        String impactLevel = fitnessClass != null && fitnessClass.getImpactLevel() != null ? fitnessClass.getImpactLevel().name() : null;

        int seatsLeft = Math.max(0, session.getCapacity() - session.getBookedCount());

        return new SessionDetailResponse(
                session.getId(),
                session.getClassId(),
                className,
                category,
                durationMinutes,
                intensity,
                impactLevel,
                session.getTrainerId(),
                trainerName,
                session.getStartsAt(),
                session.getEndsAt(),
                session.getRoom(),
                session.getCapacity(),
                session.getBookedCount(),
                seatsLeft,
                session.getStatus(),
                session.getSeriesId(),
                session.getTrainerRateSnapshotPaise(),
                myBookingStatus,
                session.getVersion(),
                session.getCreatedAt()
        );
    }
}
