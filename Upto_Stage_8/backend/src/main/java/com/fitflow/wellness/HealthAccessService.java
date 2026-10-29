package com.fitflow.wellness;

import com.fitflow.wellness.entity.HealthAccessLog;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.repository.HealthAccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthAccessService {

    private final HealthAccessLogRepository healthAccessLogRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRED)
    public void logAccess(Long memberId, Long actorUserId, HealthAccessAction action, String purpose) {
        HealthAccessLog logEntry = HealthAccessLog.builder()
                .memberId(memberId)
                .actorUserId(actorUserId)
                .action(action)
                .purpose(purpose)
                .createdAt(clock.instant())
                .build();
        healthAccessLogRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public List<HealthAccessLog> getLogsForMember(Long memberId) {
        return healthAccessLogRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
    }
}
