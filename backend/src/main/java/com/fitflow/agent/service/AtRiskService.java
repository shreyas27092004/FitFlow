package com.fitflow.agent.service;

import com.fitflow.agent.dto.AtRiskMemberDto;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AtRiskService {

    public static final String REASON_ATTENDANCE_DROP = "ATTENDANCE_DROP_50_PERCENT";
    public static final String REASON_EXPIRING_SOON = "EXPIRING_SOON_LOW_ATTENDANCE";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AtRiskMemberDto> listAtRiskMembers(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 25));
        Instant now = clock.instant();

        List<User> members = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .filter(u -> userRoleRepository.existsByIdUserIdAndIdRole(u.getId(), UserRole.Role.MEMBER))
                .toList();

        Map<Long, MembershipPlan> plansById = planRepository.findAll().stream()
                .collect(Collectors.toMap(MembershipPlan::getId, p -> p, (a, b) -> a));

        List<AtRiskMemberDto> atRisk = new ArrayList<>();

        for (User member : members) {
            AtRiskMemberDto dto = evaluateMember(member, now, plansById);
            if (dto != null) {
                atRisk.add(dto);
            }
        }

        atRisk.sort(Comparator.comparing(AtRiskMemberDto::memberId));
        return atRisk.stream().limit(effectiveLimit).toList();
    }

    @Transactional(readOnly = true)
    public boolean isMemberAtRisk(Long memberId) {
        if (memberId == null) {
            return false;
        }
        Optional<User> userOpt = userRepository.findById(memberId);
        if (userOpt.isEmpty() || userOpt.get().getStatus() != User.Status.ACTIVE) {
            return false;
        }

        Map<Long, MembershipPlan> plansById = planRepository.findAll().stream()
                .collect(Collectors.toMap(MembershipPlan::getId, p -> p, (a, b) -> a));

        AtRiskMemberDto dto = evaluateMember(userOpt.get(), clock.instant(), plansById);
        return dto != null;
    }

    private AtRiskMemberDto evaluateMember(User member, Instant now, Map<Long, MembershipPlan> plansById) {
        Long memberId = member.getId();
        List<String> reasons = new ArrayList<>();
        Long daysToExpiry = null;

        // Window 1: last 28 days
        Instant w1Start = now.minus(Duration.ofDays(28));
        long w1Attended = bookingRepository.countAttendedBookingsInWindow(memberId, w1Start, now);

        // Window 2: previous 28 days (from 56 days ago to 28 days ago)
        Instant w2Start = now.minus(Duration.ofDays(56));
        long w2Attended = bookingRepository.countAttendedBookingsInWindow(memberId, w2Start, w1Start);

        // Condition (a): attended sessions in last 28 days at least 50% lower than previous 28 days
        // (with at least 4 in earlier window)
        if (w2Attended >= 4 && (w1Attended * 2 <= w2Attended)) {
            reasons.add(REASON_ATTENDANCE_DROP);
        }

        // Condition (b): paid membership ends within 7 days AND at most 1 attended session in the last 14 days
        List<Membership> activeMemberships = membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE);
        for (Membership m : activeMemberships) {
            MembershipPlan plan = plansById.get(m.getPlanId());
            boolean isPaid = plan != null && !Boolean.TRUE.equals(plan.getIsFree()) && !"FREE".equalsIgnoreCase(plan.getCode());
            if (isPaid && m.getEndsAt() != null && m.getEndsAt().isAfter(now)) {
                long days = Duration.between(now, m.getEndsAt()).toDays();
                if (days <= 7) {
                    Instant last14Start = now.minus(Duration.ofDays(14));
                    long last14Attended = bookingRepository.countAttendedBookingsInWindow(memberId, last14Start, now);
                    if (last14Attended <= 1) {
                        reasons.add(REASON_EXPIRING_SOON);
                        daysToExpiry = days;
                        break;
                    }
                }
            }
        }

        if (reasons.isEmpty()) {
            return null;
        }

        String firstName = member.getFullName() != null ? member.getFullName().trim().split("\\s+")[0] : "Member";
        return new AtRiskMemberDto(memberId, firstName, reasons, daysToExpiry);
    }
}
