package com.fitflow.agent.service;

import com.fitflow.agent.dto.DemandInsightDto;
import com.fitflow.agent.dto.DemandInsightDto.DemandSuggestionDto;
import com.fitflow.agent.dto.DemandInsightDto.SlotStatsDto;
import com.fitflow.booking.repository.WaitlistEntryRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DemandService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final ClassSessionRepository sessionRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final FitnessClassRepository fitnessClassRepository;
    private final Clock clock;

    public record SlotKey(Long classId, int dayOfWeek, int hour) {}

    @Transactional(readOnly = true)
    public DemandInsightDto getDemandInsights(int weeks) {
        int effectiveWeeks = Math.max(4, Math.min(weeks <= 0 ? 4 : weeks, 8));
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(effectiveWeeks * 7L));

        List<ClassSession> sessions = sessionRepository.findByStartsAtBetween(from, now).stream()
                .filter(s -> s.getStatus() != ClassSession.Status.CANCELLED)
                .toList();

        Map<Long, FitnessClass> classesById = fitnessClassRepository.findAll().stream()
                .collect(Collectors.toMap(FitnessClass::getId, c -> c, (a, b) -> a));

        Map<SlotKey, List<ClassSession>> bySlot = new HashMap<>();
        for (ClassSession s : sessions) {
            ZonedDateTime zdt = s.getStartsAt().atZone(ZONE);
            SlotKey key = new SlotKey(s.getClassId(), zdt.getDayOfWeek().getValue(), zdt.getHour());
            bySlot.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<SlotStatsDto> slotStats = new ArrayList<>();
        List<DemandSuggestionDto> suggestions = new ArrayList<>();

        for (Map.Entry<SlotKey, List<ClassSession>> entry : bySlot.entrySet()) {
            SlotKey slot = entry.getKey();
            List<ClassSession> slotSessions = entry.getValue();
            FitnessClass fc = classesById.get(slot.classId());
            String className = fc != null ? fc.getName() : ("Class #" + slot.classId());

            double totalFillRate = 0.0;
            long totalWaitlist = 0;
            long totalCancelledBookings = 0;
            long totalBookings = 0;

            for (ClassSession s : slotSessions) {
                int cap = s.getCapacity() > 0 ? s.getCapacity() : 20;
                int booked = s.getBookedCount();
                double fill = cap > 0 ? (double) booked / cap : 0.0;
                totalFillRate += fill;

                long wl = waitlistEntryRepository.countBySessionId(s.getId());
                totalWaitlist += wl;
            }

            double avgFill = slotSessions.isEmpty() ? 0.0 : totalFillRate / slotSessions.size();
            double cancelRate = totalBookings > 0 ? (double) totalCancelledBookings / totalBookings : 0.0;

            SlotStatsDto stat = new SlotStatsDto(
                    slot.classId(),
                    className,
                    slot.dayOfWeek(),
                    slot.hour(),
                    Math.round(avgFill * 100.0) / 100.0,
                    totalWaitlist,
                    Math.round(cancelRate * 100.0) / 100.0
            );
            slotStats.add(stat);

            // Decision rules
            if (avgFill >= 0.85 || totalWaitlist >= 5) {
                suggestions.add(new DemandSuggestionDto(
                        "ADD_SESSION",
                        slot.classId(),
                        className,
                        slot.dayOfWeek(),
                        slot.hour(),
                        String.format("Average fill rate %.0f%% with %d waitlisted", avgFill * 100, totalWaitlist)
                ));
            } else if (avgFill <= 0.40 && slotSessions.size() >= 3) {
                suggestions.add(new DemandSuggestionDto(
                        "REDUCE_OR_MOVE",
                        slot.classId(),
                        className,
                        slot.dayOfWeek(),
                        slot.hour(),
                        String.format("Average fill rate only %.0f%% across %d sessions", avgFill * 100, slotSessions.size())
                ));
            }
        }

        slotStats.sort(Comparator.comparing(SlotStatsDto::averageFillRate).reversed());
        suggestions.sort(Comparator.comparing(DemandSuggestionDto::type).thenComparing(DemandSuggestionDto::className));

        return new DemandInsightDto(suggestions, slotStats);
    }

    @Transactional(readOnly = true)
    public boolean hasAddSessionSuggestion(Long classId, int dayOfWeek, int hour) {
        DemandInsightDto dto = getDemandInsights(4);
        return dto.suggestions().stream()
                .anyMatch(s -> "ADD_SESSION".equalsIgnoreCase(s.type())
                        && s.classId().equals(classId)
                        && s.dayOfWeek() == dayOfWeek
                        && s.hour() == hour);
    }
}
