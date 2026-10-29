package com.fitflow.wellness;

import com.fitflow.wellness.dto.FitnessProfile;
import com.fitflow.wellness.dto.InjuryDto;
import com.fitflow.wellness.dto.ScreeningResult;
import com.fitflow.wellness.model.HealthCondition;
import com.fitflow.wellness.model.InjurySeverity;
import com.fitflow.wellness.model.ScreeningStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScreeningService {

    public ScreeningResult evaluate(FitnessProfile profile) {
        if (profile == null) {
            return new ScreeningResult(ScreeningStatus.CLEAR, List.of(), false);
        }

        List<String> redFlagReasons = new ArrayList<>();
        List<String> cautionReasons = new ArrayList<>();

        // 1. Check PAR-Q questions (Q1..Q7)
        Map<String, Boolean> parq = profile.parq();
        if (parq != null) {
            for (Map.Entry<String, Boolean> entry : parq.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    redFlagReasons.add("PARQ_YES:" + entry.getKey().toUpperCase());
                }
            }
        }

        // 2. Check Conditions
        Set<HealthCondition> conditions = profile.conditions();
        if (conditions != null) {
            for (HealthCondition condition : conditions) {
                if (condition == HealthCondition.HEART_CONDITION || condition == HealthCondition.PREGNANCY) {
                    redFlagReasons.add("CONDITION:" + condition.name());
                } else {
                    cautionReasons.add("CONDITION:" + condition.name());
                }
            }
        }

        // 3. Check Injuries
        List<InjuryDto> injuries = profile.injuries();
        boolean hasSevereInjury = false;
        if (injuries != null && !injuries.isEmpty()) {
            for (InjuryDto injury : injuries) {
                cautionReasons.add("INJURY:" + injury.area().name() + ":" + injury.severity().name());
                if (injury.severity() == InjurySeverity.SEVERE) {
                    hasSevereInjury = true;
                }
            }
        }

        boolean hasOtherConsultDoctor = conditions != null && conditions.contains(HealthCondition.OTHER_CONSULT_DOCTOR);

        if (!redFlagReasons.isEmpty()) {
            return new ScreeningResult(
                    ScreeningStatus.RED_FLAG,
                    redFlagReasons,
                    true // doctorCheckRecommended is always true for RED_FLAG
            );
        }

        if (!cautionReasons.isEmpty()) {
            boolean doctorCheckRecommended = hasSevereInjury || hasOtherConsultDoctor;
            return new ScreeningResult(
                    ScreeningStatus.CAUTION,
                    cautionReasons,
                    doctorCheckRecommended
            );
        }

        return new ScreeningResult(
                ScreeningStatus.CLEAR,
                List.of(),
                false
        );
    }
}
