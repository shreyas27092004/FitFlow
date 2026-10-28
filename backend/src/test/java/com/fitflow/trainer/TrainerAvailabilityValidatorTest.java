package com.fitflow.trainer;

import com.fitflow.common.error.ApiException;
import com.fitflow.common.error.ErrorCode;
import com.fitflow.trainer.dto.AvailabilitySlotDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainerAvailabilityValidatorTest {

    private final TrainerAvailabilityValidator validator = new TrainerAvailabilityValidator();

    @Test
    @DisplayName("Validation: null or empty slot list passes without error")
    void testNullOrEmptyPasses() {
        assertDoesNotThrow(() -> validator.validate(null));
        assertDoesNotThrow(() -> validator.validate(Collections.emptyList()));
    }

    @Test
    @DisplayName("Validation: valid non-overlapping slots pass")
    void testValidNonOverlappingSlotsPass() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(1, "09:00", "12:00"),
                new AvailabilitySlotDto(1, "14:00", "18:00"),
                new AvailabilitySlotDto(2, "10:00", "13:00")
        );
        assertDoesNotThrow(() -> validator.validate(slots));
    }

    @Test
    @DisplayName("Validation: adjacent boundary slots (start == previous end) pass")
    void testAdjacentSlotsPass() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(1, "09:00", "12:00"),
                new AvailabilitySlotDto(1, "12:00", "15:00")
        );
        assertDoesNotThrow(() -> validator.validate(slots));
    }

    @Test
    @DisplayName("Validation: start equals end throws VALIDATION_FAILED")
    void testStartEqualsEndThrows() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(1, "10:00", "10:00")
        );
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(slots));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Validation: end before start throws VALIDATION_FAILED")
    void testEndBeforeStartThrows() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(1, "14:00", "10:00")
        );
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(slots));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Validation: dayOfWeek outside 1..7 throws VALIDATION_FAILED")
    void testInvalidDayOfWeekThrows() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(0, "09:00", "11:00")
        );
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(slots));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());

        List<AvailabilitySlotDto> slots8 = List.of(
                new AvailabilitySlotDto(8, "09:00", "11:00")
        );
        ApiException ex8 = assertThrows(ApiException.class, () -> validator.validate(slots8));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex8.getErrorCode());
    }

    @Test
    @DisplayName("Validation: overlapping slots on the same day throw VALIDATION_FAILED")
    void testOverlappingSlotsOnSameDayThrows() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(3, "09:00", "12:00"),
                new AvailabilitySlotDto(3, "11:30", "14:00")
        );
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(slots));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Validation: slot fully contained inside another throws VALIDATION_FAILED")
    void testContainedSlotThrows() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(4, "08:00", "16:00"),
                new AvailabilitySlotDto(4, "10:00", "12:00")
        );
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(slots));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Validation: same time slot on different days passes")
    void testSameTimeSlotOnDifferentDaysPasses() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(1, "09:00", "12:00"),
                new AvailabilitySlotDto(2, "09:00", "12:00"),
                new AvailabilitySlotDto(3, "09:00", "12:00")
        );
        assertDoesNotThrow(() -> validator.validate(slots));
    }

    @Test
    @DisplayName("Validation: invalid time string throws VALIDATION_FAILED")
    void testInvalidTimeStringThrows() {
        List<AvailabilitySlotDto> slots = List.of(
                new AvailabilitySlotDto(1, "invalid", "12:00")
        );
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(slots));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }
}
