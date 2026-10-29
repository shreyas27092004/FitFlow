package com.fitflow.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.entity.PendingAction;
import com.fitflow.agent.repository.PendingActionRepository;
import com.fitflow.agent.tools.dto.ProposeBookingArgs;
import com.fitflow.agent.tools.dto.ProposeCancelBookingArgs;
import com.fitflow.agent.tools.dto.ProposeMembershipChangeArgs;
import com.fitflow.agent.tools.dto.ProposeProfileSaveArgs;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.common.error.ApiException;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangeMembershipRequest;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.membership.dto.SubscribeRequest;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import com.fitflow.wellness.WellnessProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    public static final long EXPIRY_SECONDS = 600; // 10 minutes

    private final PendingActionRepository pendingActionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final BookingService bookingService;
    private final MembershipService membershipService;
    private final WellnessProfileService wellnessProfileService;
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

        if (!userRoleRepository.existsByIdUserIdAndIdRole(userId, UserRole.Role.MEMBER)) {
            throw ApiException.forbidden("Caller does not have MEMBER role");
        }

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
                        // Subscribe or Change
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
        List<PendingAction> pending = pendingActionRepository.findByUserIdAndStatus(null, PendingAction.Status.PENDING.name());
        for (PendingAction p : pending) {
            if (p.getExpiresAt().isBefore(now)) {
                p.setStatus(PendingAction.Status.EXPIRED.name());
                p.setDecidedAt(now);
                pendingActionRepository.save(p);
            }
        }
    }
}
