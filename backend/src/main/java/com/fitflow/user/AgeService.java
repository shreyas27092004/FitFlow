package com.fitflow.user;

import com.fitflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class AgeService {

    private final Clock clock;

    public boolean is14Plus(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (!dateOfBirth.isBefore(today)) {
            return false;
        }
        return Period.between(dateOfBirth, today).getYears() >= 14;
    }

    public boolean is18Plus(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        if (!dateOfBirth.isBefore(today)) {
            return false;
        }
        return Period.between(dateOfBirth, today).getYears() >= 18;
    }

    public void validateRegistrationAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw ApiException.ageRestricted("Date of birth is required");
        }
        LocalDate today = LocalDate.now(clock);
        if (!dateOfBirth.isBefore(today)) {
            throw ApiException.ageRestricted("Date of birth must be in the past");
        }
        if (Period.between(dateOfBirth, today).getYears() < 14) {
            throw ApiException.ageRestricted("User must be at least 14 years old to register");
        }
    }
}
