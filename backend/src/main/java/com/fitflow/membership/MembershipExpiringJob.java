package com.fitflow.membership;

import com.fitflow.common.job.JobLockRepository;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipNotice;
import com.fitflow.membership.entity.MembershipNoticeId;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.repository.MembershipNoticeRepository;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import com.fitflow.notification.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("membershipExpiringJob")
@RequiredArgsConstructor
public class MembershipExpiringJob {

    private static final String JOB_NAME = "membership_expiring_job";
    public static final String NOTICE_KIND_3_DAYS = "EXPIRING_3_DAYS";

    private final MembershipRepository membershipRepository;
    private final MembershipNoticeRepository membershipNoticeRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final OutboxService outboxService;
    private final JobLockRepository jobLockRepository;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    @Scheduled(fixedRate = 3600000) // Hourly
    public void runScheduled() {
        processExpiringMemberships();
    }

    @Transactional
    public int processExpiringMemberships() {
        Instant now = clock.instant();
        Instant expiredBefore = now.minusSeconds(600);

        int acquired = 0;
        try {
            acquired = jobLockRepository.acquireLock(JOB_NAME, now, instanceId, expiredBefore);
        } catch (Exception e) {
            log.warn("JobLock acquire failed for {}: {}", JOB_NAME, e.getMessage());
        }

        if (acquired == 0) {
            log.debug("Membership expiring job lock held by another instance. Skipping.");
            return 0;
        }

        try {
            // Window: 3 days (+-1h) = 71 hours to 73 hours from now
            Instant windowStart = now.plus(Duration.ofHours(71));
            Instant windowEnd = now.plus(Duration.ofHours(73));

            List<Membership> memberships = membershipRepository.findActiveExpiringMembershipsNeedingNotice(
                    windowStart, windowEnd, NOTICE_KIND_3_DAYS
            );
            log.info("Found {} memberships expiring between {} and {}", memberships.size(), windowStart, windowEnd);

            int count = 0;
            for (Membership membership : memberships) {
                MembershipNoticeId noticeId = new MembershipNoticeId(membership.getId(), NOTICE_KIND_3_DAYS);
                if (membershipNoticeRepository.existsById(noticeId)) {
                    continue;
                }

                MembershipNotice notice = MembershipNotice.builder()
                        .id(noticeId)
                        .createdAt(now)
                        .build();
                membershipNoticeRepository.save(notice);

                String planName = membershipPlanRepository.findById(membership.getPlanId())
                        .map(MembershipPlan::getName)
                        .orElse("Paid Membership");

                outboxService.append(
                        "MEMBERSHIP",
                        membership.getId(),
                        "membership.expiring",
                        Map.of(
                                "membershipId", membership.getId(),
                                "userId", membership.getMemberId(),
                                "planName", planName,
                                "endsAt", membership.getEndsAt() != null ? membership.getEndsAt().toString() : ""
                        )
                );
                count++;
            }

            return count;
        } finally {
            try {
                jobLockRepository.releaseLock(JOB_NAME, instanceId);
            } catch (Exception ignored) {}
        }
    }
}
