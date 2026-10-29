package com.fitflow.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
// Stage 9 services (now in agent.service)
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.entity.PendingAction;
import com.fitflow.agent.repository.PendingActionRepository;
import com.fitflow.agent.tools.dto.ProposeAddSessionArgs;
import com.fitflow.agent.tools.dto.ProposeBookingArgs;
import com.fitflow.agent.tools.dto.ProposeCancelBookingArgs;
import com.fitflow.agent.tools.dto.ProposeMembershipChangeArgs;
import com.fitflow.agent.tools.dto.ProposeProfileSaveArgs;
import com.fitflow.agent.tools.dto.ProposeRetentionOfferArgs;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.FitnessClassService;
import com.fitflow.fitnessclass.dto.ClassDetailResponse;
import com.fitflow.fitnessclass.dto.CreateClassRequest;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangeMembershipRequest;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.membership.dto.SubscribeRequest;
import com.fitflow.notification.OutboxService;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.session.ClassSessionService;
import com.fitflow.session.dto.CreateSessionRequest;
import com.fitflow.session.dto.SessionDetailResponse;
import com.fitflow.user.dto.NotificationPrefsDto;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import com.fitflow.wellness.WellnessProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    public static final long EXPIRY_SECONDS = 600; // 10 minutes
    public static volatile boolean sanityBreakSkipAtRiskRecheck = false;

    private final PendingActionRepository pendingActionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AdminPermissionRepository adminPermissionRepository;
    private final BookingService bookingService;
    private final MembershipService membershipService;
    private final WellnessProfileService wellnessProfileService;
    private final WeeklyPlanService weeklyPlanService;
    private final AtRiskService atRiskService;
    private final FitnessClassService fitnessClassService;
    private final ClassSessionService classSessionService;
    private final DemandService demandService;
    private final OutboxService outboxService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Transactional
    public PendingActionProposal propose(Long conversationId, Long userId, String toolName, Object args, String summary) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(EXPIRY_SECONDS);

        String argsJson;
        try {
            argsJson = objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid tool arguments: " + e.getMessage());
        }

        PendingAction action = PendingAction.builder()
                .conversationId(conversationId)
                .userId(userId)
                .toolName(toolName)
                .argsJson(argsJson)
                .summary(summary)
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();

        PendingAction saved = pendingActionRepository.save(action);
        return new PendingActionProposal(saved.getId(), saved.getToolName(), saved.getSummary(), saved.getExpiresAt());
    }

    @Transactional
    public Map<String, Object> confirm(Long actionId, Long userId) {
        PendingAction action = pendingActionRepository.findById(actionId)
                .orElseThrow(() -> ApiException.notFound("Pending action not found with id: " + actionId));

        // Ownership check (IDOR gate)
        if (!action.getUserId().equals(userId)) {
            throw ApiException.notFound("Pending action not found with id: " + actionId);
        }

        // Idempotency: second confirm returns stored result without re-executing
        if (PendingAction.Status.CONFIRMED.name().equalsIgnoreCase(action.getStatus())) {
            log.info("PendingAction id={} already CONFIRMED. Returning cached result.", actionId);
            try {
                if (action.getResultJson() != null) {
                    return objectMapper.readValue(action.getResultJson(), new TypeReference<>() {});
                }
                return Map.of("status", "CONFIRMED");
            } catch (Exception e) {
                return Map.of("status", "CONFIRMED");
            }
        }

        if (PendingAction.Status.REJECTED.name().equalsIgnoreCase(action.getStatus())) {
            throw ApiException.conflict("Action has already been rejected");
        }

        Instant now = clock.instant();
        if (PendingAction.Status.EXPIRED.name().equalsIgnoreCase(action.getStatus())
                || action.getExpiresAt().isBefore(now)) {
            action.setStatus(PendingAction.Status.EXPIRED.name());
            action.setDecidedAt(now);
            pendingActionRepository.save(action);
            throw ApiException.conflict("Action has expired");
        }

        // Re-check caller roles and permissions NOW
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.forbidden("User no longer exists"));

        validateCallerPrivilegesForAction(userId, action.getToolName());

        String idempotencyKey = "agent-" + (actionId < 10 ? "0" + actionId : actionId);
        Map<String, Object> result = new HashMap<>();

        try {
            switch (action.getToolName()) {
                case "propose_booking" -> {
                    ProposeBookingArgs args = objectMapper.readValue(action.getArgsJson(), ProposeBookingArgs.class);
                    bookingService.createBooking(new CreateBookingRequest(args.sessionId()), idempotencyKey, userId);
                    result.put("status", "CONFIRMED");
                    result.put("sessionId", args.sessionId());
                    result.put("message", "Booking confirmed successfully");
                }
                case "propose_cancel_booking" -> {
                    ProposeCancelBookingArgs args = objectMapper.readValue(action.getArgsJson(), ProposeCancelBookingArgs.class);
                    bookingService.cancelBooking(args.bookingId(), userId, false, "Cancelled via FitFlow Agent");
                    result.put("status", "CANCELLED");
                    result.put("bookingId", args.bookingId());
                    result.put("message", "Booking cancelled successfully");
                }
                case "propose_membership_change" -> {
                    ProposeMembershipChangeArgs args = objectMapper.readValue(action.getArgsJson(), ProposeMembershipChangeArgs.class);
                    if ("CANCEL".equalsIgnoreCase(args.action())) {
                        Map<String, Object> cancelRes = membershipService.cancelMembership(userId, idempotencyKey);
                        result.putAll(cancelRes);
                    } else {
                        MemberMembershipSummaryResponse summary = membershipService.getMemberMembership(userId);
                        boolean hasPaidPlan = summary.paid() != null;
                        if (hasPaidPlan) {
                            ChangePreviewResponse preview = membershipService.changePreview(
                                     userId, new ChangePreviewRequest(args.targetPlanCode(), null));
                            Object changeRes = membershipService.changeMembership(
                                    userId, idempotencyKey, new ChangeMembershipRequest(args.targetPlanCode(), preview.amountDuePaise()));
                            result.putAll(objectMapper.convertValue(changeRes, new TypeReference<Map<String, Object>>() {}));
                        } else {
                            PaymentIntentResponse pi = membershipService.subscribe(
                                    userId, idempotencyKey, new SubscribeRequest(args.targetPlanCode()));
                            result.putAll(objectMapper.convertValue(pi, new TypeReference<Map<String, Object>>() {}));
                        }
                    }
                }
                case "propose_profile_save" -> {
                    ProposeProfileSaveArgs args = objectMapper.readValue(action.getArgsJson(), ProposeProfileSaveArgs.class);
                    wellnessProfileService.saveProfile(userId, args.profileDraft());
                    result.put("status", "SAVED");
                    result.put("message", "Wellness profile saved successfully");
                }
                case "propose_weekly_plan" -> {
                    Map<String, Object> planArgs = objectMapper.readValue(action.getArgsJson(), Map.class);
                    Long planId = ((Number) planArgs.get("planId")).longValue();
                    Map<String, Object> execResult = weeklyPlanService.executeWeeklyPlanConfirmation(planId, userId, actionId);
                    result.putAll(execResult);
                }
                case "propose_retention_offer" -> {
                    ProposeRetentionOfferArgs args = objectMapper.readValue(action.getArgsJson(), ProposeRetentionOfferArgs.class);
                    if (!sanityBreakSkipAtRiskRecheck) {
                        if (!atRiskService.isMemberAtRisk(args.targetMemberId())) {
                            throw ApiException.conflict("Member " + args.targetMemberId() + " is no longer in the at-risk list");
                        }
                    }

                    if ("NUDGE_EMAIL".equalsIgnoreCase(args.offerType())) {
                        User targetUser = userRepository.findById(args.targetMemberId())
                                .orElseThrow(() -> ApiException.notFound("Target member not found with id: " + args.targetMemberId()));

                        boolean optedOut = false;
                        if (targetUser.getNotificationPrefs() != null) {
                            try {
                                NotificationPrefsDto prefs = objectMapper.readValue(targetUser.getNotificationPrefs(), NotificationPrefsDto.class);
                                if (Boolean.FALSE.equals(prefs.emailMarketing())) {
                                    optedOut = true;
                                }
                            } catch (Exception ignored) {}
                        }

                        if (optedOut) {
                            result.put("status", "SKIPPED");
                            result.put("message", "skipped: member opted out");
                        } else {
                            outboxService.append("USER", args.targetMemberId(), "retention.nudge", Map.of(
                                    "memberId", args.targetMemberId(),
                                    "email", targetUser.getEmail(),
                                    "firstName", targetUser.getFullName() != null ? targetUser.getFullName().trim().split("\\s+")[0] : "Member",
                                    "offerType", "NUDGE_EMAIL"
                            ));
                            result.put("status", "CONFIRMED");
                            result.put("message", "Retention nudge email event emitted");
                        }
                    } else if ("EXTRA_FREE_SESSION".equalsIgnoreCase(args.offerType())) {
                        membershipService.grantFreeSession(args.targetMemberId(), 1, "Retention offer via FitFlow Agent", userId);
                        result.put("status", "CONFIRMED");
                        result.put("message", "Extra free session granted successfully");
                    }
                }
                case "propose_create_class" -> {
                    Map<String, Object> draft = objectMapper.readValue(action.getArgsJson(), Map.class);
                    String name = (String) draft.get("name");
                    String desc = (String) draft.get("description");
                    String catStr = (String) draft.get("category");
                    String intensityStr = (String) draft.get("intensity");
                    String impactStr = (String) draft.get("impactLevel");
                    Number metNum = (Number) draft.get("metValue");
                    Number minAge = (Number) draft.get("minAge");
                    Number cap = (Number) draft.get("defaultCapacity");
                    Number dur = (Number) draft.get("durationMinutes");
                    List<String> tags = (List<String>) draft.get("contraindicationTags");

                    CreateClassRequest req = new CreateClassRequest(
                            name,
                            desc,
                            ClassCategory.valueOf(catStr.toUpperCase(Locale.ROOT)),
                            dur != null ? dur.intValue() : 60,
                            ClassIntensity.valueOf(intensityStr.toUpperCase(Locale.ROOT)),
                            ClassImpactLevel.valueOf(impactStr.toUpperCase(Locale.ROOT)),
                            metNum != null ? BigDecimal.valueOf(metNum.doubleValue()) : BigDecimal.valueOf(4.0),
                            tags,
                            minAge != null ? minAge.intValue() : 14,
                            cap != null ? cap.intValue() : 20
                    );

                    ClassDetailResponse created = fitnessClassService.createDraftClass(req, userId);
                    result.put("status", "CONFIRMED");
                    result.put("classId", created.id());
                    result.put("className", created.name());
                    result.put("metadataReviewed", false);
                    result.put("message", "Draft only: please review metadata before members can see this class.");
                }
                case "propose_add_session" -> {
                    ProposeAddSessionArgs args = objectMapper.readValue(action.getArgsJson(), ProposeAddSessionArgs.class);
                    ZonedDateTime zdt = args.startsAt().atZone(DemandService.ZONE);
                    if (!demandService.hasAddSessionSuggestion(args.classId(), zdt.getDayOfWeek().getValue(), zdt.getHour())) {
                        throw ApiException.conflict("Session slot does not match a current ADD_SESSION recommendation");
                    }

                    CreateSessionRequest req = new CreateSessionRequest(
                            args.classId(),
                            args.assignedTrainerId(),
                            args.startsAt(),
                            null,
                            args.room(),
                            null
                    );

                    List<SessionDetailResponse> created = classSessionService.createSessions(req);
                    result.put("status", "CONFIRMED");
                    result.put("sessionId", created.get(0).id());
                    result.put("message", "Session added successfully");
                }
                default -> throw ApiException.badRequest("Unknown pending action tool: " + action.getToolName());
            }

            action.setStatus(PendingAction.Status.CONFIRMED.name());
            action.setDecidedAt(now);
            action.setResultJson(objectMapper.writeValueAsString(result));
            pendingActionRepository.save(action);

            return result;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute pending action id={}: {}", actionId, e.getMessage(), e);
            throw ApiException.internalError("Action execution failed: " + e.getMessage());
        }
    }

    private void validateCallerPrivilegesForAction(Long userId, String toolName) {
        if (userId == null) {
            return;
        }

        boolean isMemberAction = toolName.equals("propose_booking")
                || toolName.equals("propose_cancel_booking")
                || toolName.equals("propose_membership_change")
                || toolName.equals("propose_profile_save")
                || toolName.equals("propose_weekly_plan");

        if (isMemberAction) {
            if (!userRoleRepository.existsByIdUserIdAndIdRole(userId, UserRole.Role.MEMBER)) {
                throw ApiException.forbidden("Caller does not have MEMBER role");
            }
            return;
        }

        boolean isAdminAction = toolName.equals("propose_retention_offer")
                || toolName.equals("propose_create_class")
                || toolName.equals("propose_add_session");

        if (isAdminAction) {
            if (!userRoleRepository.existsByIdUserIdAndIdRole(userId, UserRole.Role.ADMIN)) {
                throw ApiException.forbidden("Caller does not have ADMIN role");
            }
            List<AdminPermission> perms = adminPermissionRepository.findByIdUserId(userId);
            boolean isSuperAdmin = perms.stream().anyMatch(p -> "SUPER_ADMIN".equalsIgnoreCase(p.getId().getPermission()));
            if (toolName.equals("propose_retention_offer")) {
                boolean hasUserEdit = isSuperAdmin || perms.stream().anyMatch(p -> "USER_EDIT".equalsIgnoreCase(p.getId().getPermission()));
                if (!hasUserEdit) {
                    throw ApiException.forbidden("Caller lacks USER_EDIT permission");
                }
            } else {
                boolean hasClassEdit = isSuperAdmin || perms.stream().anyMatch(p -> "CLASS_EDIT".equalsIgnoreCase(p.getId().getPermission()));
                if (!hasClassEdit) {
                    throw ApiException.forbidden("Caller lacks CLASS_EDIT permission");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PendingActionProposal> listPending(Long userId) {
        return pendingActionRepository.findByUserIdAndStatus(userId, PendingAction.Status.PENDING.name()).stream()
                .map(a -> new PendingActionProposal(a.getId(), a.getToolName(), a.getSummary(), a.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void reject(Long actionId, Long userId) {
        PendingAction action = pendingActionRepository.findById(actionId)
                .orElseThrow(() -> ApiException.notFound("Pending action not found with id: " + actionId));

        if (!action.getUserId().equals(userId)) {
            throw ApiException.notFound("Pending action not found with id: " + actionId);
        }

        if (!PendingAction.Status.PENDING.name().equalsIgnoreCase(action.getStatus())) {
            throw ApiException.conflict("Action is not in PENDING status: " + action.getStatus());
        }

        action.setStatus(PendingAction.Status.REJECTED.name());
        action.setDecidedAt(clock.instant());
        pendingActionRepository.save(action);
    }

    @Transactional
    public void expireOldActions() {
        Instant now = clock.instant();
        List<PendingAction> pending = pendingActionRepository.findByStatusAndExpiresAtBefore(
                PendingAction.Status.PENDING.name(), now);
        for (PendingAction p : pending) {
            p.setStatus(PendingAction.Status.EXPIRED.name());
            p.setDecidedAt(now);
            pendingActionRepository.save(p);
        }
    }
}
