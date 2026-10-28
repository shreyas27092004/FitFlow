package com.fitflow.trainer;

import com.fitflow.common.error.ApiException;
import com.fitflow.trainer.dto.AvailabilitySlotDto;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TrainerAvailabilityValidator {

    public void validate(List<AvailabilitySlotDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }

        // 1. Validate each slot individually
        for (AvailabilitySlotDto slot : slots) {
            if (slot.dayOfWeek() < 1 || slot.dayOfWeek() > 7) {
                throw ApiException.validationFailed("dayOfWeek must be between 1 and 7 (found: " + slot.dayOfWeek() + ")");
            }

            LocalTime start;
            LocalTime end;
            try {
                start = LocalTime.parse(slot.start());
                end = LocalTime.parse(slot.end());
            } catch (DateTimeParseException e) {
                throw ApiException.validationFailed("Invalid time format (expected HH:mm): " + e.getMessage());
            }

            if (!end.isAfter(start)) {
                throw ApiException.validationFailed(
                        String.format("End time (%s) must be strictly after start time (%s) for day %d",
                                slot.end(), slot.start(), slot.dayOfWeek())
                );
            }
        }

        // 2. Group by dayOfWeek and check for overlapping slots
        Map<Integer, List<AvailabilitySlotDto>> byDay = slots.stream()
                .collect(Collectors.groupingBy(AvailabilitySlotDto::dayOfWeek));

        for (Map.Entry<Integer, List<AvailabilitySlotDto>> entry : byDay.entrySet()) {
            int day = entry.getKey();
            List<AvailabilitySlotDto> daySlots = entry.getValue().stream()
                    .sorted(Comparator.comparing(s -> LocalTime.parse(s.start())))
                    .toList();

            for (int i = 0; i < daySlots.size() - 1; i++) {
                AvailabilitySlotDto current = daySlots.get(i);
                AvailabilitySlotDto next = daySlots.get(i + 1);

                LocalTime currentEnd = LocalTime.parse(current.end());
                LocalTime nextStart = LocalTime.parse(next.start());

                if (nextStart.isBefore(currentEnd)) {
                    throw ApiException.validationFailed(
                            String.format("Overlapping availability slots detected on day %d: [%s - %s] and [%s - %s]",
                                    day, current.start(), current.end(), next.start(), next.end())
                    );
                }
            }
        }
    }
}
