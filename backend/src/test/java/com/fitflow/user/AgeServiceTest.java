package com.fitflow.user;

import com.fitflow.common.error.ApiException;
import com.fitflow.testutil.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgeServiceTest {

    // Reference time: 2026-10-28
    private final MutableClock clock = MutableClock.ofUtc(Instant.parse("2026-10-28T00:00:00Z"));
    private AgeService ageService;

    @BeforeEach
    void setUp() {
        ageService = new AgeService(clock);
    }

    @Test
    @DisplayName("Age 14 and above passes registration age check")
    void test14PlusValid() {
        // Born 2012-10-28 -> exactly 14 years old
        LocalDate dob = LocalDate.of(2012, 10, 28);
        assertTrue(ageService.is14Plus(dob));
        assertDoesNotThrow(() -> ageService.validateRegistrationAge(dob));
    }

    @Test
    @DisplayName("Age under 14 fails registration age check and throws AGE_RESTRICTED")
    void testUnder14Rejected() {
        // Born 2012-10-29 -> 13 years and 364 days
        LocalDate dob = LocalDate.of(2012, 10, 29);
        assertFalse(ageService.is14Plus(dob));
        ApiException ex = assertThrows(ApiException.class, () -> ageService.validateRegistrationAge(dob));
        assertTrue(ex.getMessage().contains("at least 14 years old"));
    }

    @Test
    @DisplayName("Age 18 check identifies adults for health features")
    void test18PlusCheck() {
        LocalDate adultDob = LocalDate.of(2008, 10, 28); // exactly 18
        LocalDate minorDob = LocalDate.of(2008, 10, 29); // 17 years and 364 days

        assertTrue(ageService.is18Plus(adultDob));
        assertFalse(ageService.is18Plus(minorDob));
    }

    @Test
    @DisplayName("Future birth date is rejected")
    void testFutureBirthDateRejected() {
        LocalDate futureDob = LocalDate.of(2026, 10, 29);
        assertFalse(ageService.is14Plus(futureDob));
        ApiException ex = assertThrows(ApiException.class, () -> ageService.validateRegistrationAge(futureDob));
        assertTrue(ex.getMessage().contains("in the past"));
    }
}
