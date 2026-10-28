package com.fitflow.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.messaging.EventEnvelope;
import com.fitflow.messaging.EventHandler;
import com.fitflow.messaging.RabbitMqTopologyConfig;
import com.fitflow.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationConsumer implements EventHandler {

    public enum Category {
        TRANSACTIONAL, BOOKINGS, REMINDERS, MARKETING
    }

    private final EmailService emailService;
    private final NotificationRecipientDao recipientDao;
    private final NotificationLogDao notificationLogDao;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public boolean supports(String eventType) {
        return RabbitMqTopologyConfig.NOTIFICATION_EMAIL_ROUTING_KEYS.contains(eventType);
    }

    @Override
    public void handle(EventEnvelope envelope) {
        String eventType = envelope.eventType();
        Map<String, Object> payload = envelope.payload() != null ? envelope.payload() : Map.of();

        log.info("Processing notification event id={} type='{}'", envelope.eventId(), eventType);

        switch (eventType) {
            case "user.created" -> handleUserCreated(envelope, payload);
            case "password.reset_requested" -> handlePasswordResetRequested(envelope, payload);
            case "user.suspended" -> handleUserSuspended(envelope, payload);
            case "booking.created" -> handleBookingCreated(envelope, payload);
            case "booking.cancelled" -> handleBookingCancelled(envelope, payload);
            case "waitlist.promoted" -> handleWaitlistPromoted(envelope, payload);
            case "session.cancelled" -> handleSessionCancelled(envelope, payload);
            case "session.rescheduled" -> handleSessionRescheduled(envelope, payload);
            case "session.reminder" -> handleSessionReminder(envelope, payload);
            case "membership.started" -> handleMembershipStarted(envelope, payload);
            case "membership.changed" -> handleMembershipChanged(envelope, payload);
            case "membership.expiring" -> handleMembershipExpiring(envelope, payload);
            case "membership.expired" -> handleMembershipExpired(envelope, payload);
            case "payment.succeeded" -> handlePaymentSucceeded(envelope, payload);
            case "payment.failed" -> handlePaymentFailed(envelope, payload);
            case "refund.pending_approval" -> handleRefundPendingApproval(envelope, payload);
            case "refund.issued" -> handleRefundIssued(envelope, payload);
            case "payout.approved" -> handlePayoutApproved(envelope, payload);
            case "feedback.pain_reported" -> handleFeedbackPainReported(envelope, payload);
            default -> log.warn("Unhandled notification event type: {}", eventType);
        }
    }

    private void handleUserCreated(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        String tempPassword = "";

        if (envelope.enc() != null && !envelope.enc().isBlank()) {
            try {
                String decryptedJson = encryptionService.decryptString(envelope.enc());
                Map<String, Object> secrets = objectMapper.readValue(decryptedJson, new TypeReference<>() {});
                tempPassword = (String) secrets.getOrDefault("tempPassword", "");
            } catch (Exception e) {
                log.error("Failed to decrypt temporary password for user.created event {}: {}", envelope.eventId(), e.getMessage());
            }
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("tempPassword", tempPassword);
        vars.put("loginUrl", emailService.getFrontendBaseUrl() + "/login");

        sendNotification(userId, "welcome-created", "Welcome to FitFlow!", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handlePasswordResetRequested(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        String token = "";

        if (envelope.enc() != null && !envelope.enc().isBlank()) {
            try {
                String decryptedJson = encryptionService.decryptString(envelope.enc());
                Map<String, Object> secrets = objectMapper.readValue(decryptedJson, new TypeReference<>() {});
                token = (String) secrets.getOrDefault("token", "");
            } catch (Exception e) {
                log.error("Failed to decrypt password reset token for event {}: {}", envelope.eventId(), e.getMessage());
            }
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("resetUrl", emailService.getFrontendBaseUrl() + "/reset-password?token=" + token);

        // Subject never contains token
        sendNotification(userId, "password-reset", "Reset your FitFlow password", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handleUserSuspended(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("reason", payload.getOrDefault("reason", "Administrative policy violation"));

        // Suspension notice sends even to SUSPENDED users
        sendNotificationInternal(userId, "account-suspended", "Your FitFlow account has been suspended", Category.TRANSACTIONAL, vars, envelope.eventId(), true);
    }

    private Long getUserId(Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        if (userId == null) {
            userId = getLong(payload, "memberId");
        }
        return userId;
    }

    private void handleBookingCreated(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getUserId(payload);
        Map<String, Object> vars = new HashMap<>();
        vars.put("className", payload.getOrDefault("className", "Fitness Class"));
        Object startTime = payload.get("sessionStartTime") != null ? payload.get("sessionStartTime") : payload.get("startsAt");
        vars.put("sessionStartTime", formatTime(startTime));
        vars.put("trainerName", payload.getOrDefault("trainerName", "Fitness Trainer"));

        sendNotification(userId, "booking-confirmed", "Booking Confirmed", Category.BOOKINGS, vars, envelope.eventId());
    }

    private void handleBookingCancelled(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getUserId(payload);
        Map<String, Object> vars = new HashMap<>();
        vars.put("className", payload.getOrDefault("className", "Fitness Class"));
        Object startTime = payload.get("sessionStartTime") != null ? payload.get("sessionStartTime") : payload.get("startsAt");
        vars.put("sessionStartTime", formatTime(startTime));

        sendNotification(userId, "booking-cancelled", "Booking Cancelled", Category.BOOKINGS, vars, envelope.eventId());
    }

    private void handleWaitlistPromoted(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getUserId(payload);
        Map<String, Object> vars = new HashMap<>();
        vars.put("className", payload.getOrDefault("className", "Fitness Class"));
        Object startTime = payload.get("sessionStartTime") != null ? payload.get("sessionStartTime") : payload.get("startsAt");
        vars.put("sessionStartTime", formatTime(startTime));

        sendNotification(userId, "waitlist-promoted", "You have been promoted from the waitlist!", Category.BOOKINGS, vars, envelope.eventId());
    }

    private void handleSessionCancelled(EventEnvelope envelope, Map<String, Object> payload) {
        Long sessionId = getLong(payload, "sessionId");
        String className = (String) payload.getOrDefault("className", "Fitness Class");
        String sessionStartTime = formatTime(payload.get("sessionStartTime"));
        String reason = (String) payload.getOrDefault("reason", "Operational reasons");

        List<Long> memberIds = recipientDao.findConfirmedBookingUserIdsForSession(sessionId);
        for (Long memberId : memberIds) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("className", className);
            vars.put("sessionStartTime", sessionStartTime);
            vars.put("reason", reason);

            sendNotification(memberId, "session-cancelled", "Important: Class Session Cancelled", Category.TRANSACTIONAL, vars, envelope.eventId());
        }
    }

    private void handleSessionRescheduled(EventEnvelope envelope, Map<String, Object> payload) {
        Long sessionId = getLong(payload, "sessionId");
        String className = (String) payload.getOrDefault("className", "Fitness Class");
        String oldStartTime = formatTime(payload.get("oldStartTime"));
        String newStartTime = formatTime(payload.get("newStartTime"));
        String trainerName = (String) payload.getOrDefault("trainerName", "Trainer");

        List<Long> recipientIds = recipientDao.findConfirmedBookingUserIdsForSession(sessionId);
        Optional<Long> trainerUserId = recipientDao.findTrainerUserIdForSession(sessionId);
        if (trainerUserId.isPresent() && !recipientIds.contains(trainerUserId.get())) {
            recipientIds.add(trainerUserId.get());
        }

        for (Long recipientId : recipientIds) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("className", className);
            vars.put("oldStartTime", oldStartTime);
            vars.put("newStartTime", newStartTime);
            vars.put("trainerName", trainerName);

            sendNotification(recipientId, "session-rescheduled", "Class Session Rescheduled", Category.TRANSACTIONAL, vars, envelope.eventId());
        }
    }

    private void handleSessionReminder(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("className", payload.getOrDefault("className", "Fitness Class"));
        vars.put("sessionStartTime", formatTime(payload.get("sessionStartTime")));

        sendNotification(userId, "session-reminder", "Reminder: Upcoming Session Tomorrow", Category.REMINDERS, vars, envelope.eventId());
    }

    private void handleMembershipStarted(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("planName", payload.getOrDefault("planName", "Membership Plan"));
        vars.put("endsAt", formatTime(payload.get("endsAt")));

        sendNotification(userId, "membership-started", "Your FitFlow Membership is Active", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handleMembershipChanged(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("oldPlanName", payload.getOrDefault("oldPlanName", "Previous Plan"));
        vars.put("newPlanName", payload.getOrDefault("newPlanName", "New Plan"));
        vars.put("creditFormatted", formatPaise(payload.get("creditPaise")));
        vars.put("amountDueFormatted", formatPaise(payload.get("amountDuePaise")));
        vars.put("refundFormatted", formatPaise(payload.get("refundPaise")));
        vars.put("endsAt", formatTime(payload.get("endsAt")));

        sendNotification(userId, "membership-changed", "FitFlow Membership Updated", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handleMembershipExpiring(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("planName", payload.getOrDefault("planName", "Membership Plan"));
        vars.put("endsAt", formatTime(payload.get("endsAt")));

        sendNotification(userId, "membership-expiring", "Your Membership is Expiring Soon", Category.REMINDERS, vars, envelope.eventId());
    }

    private void handleMembershipExpired(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("planName", payload.getOrDefault("planName", "Membership Plan"));

        sendNotification(userId, "membership-expired", "Your FitFlow Membership Has Expired", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handlePaymentSucceeded(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceNumber", payload.getOrDefault("invoiceNumber", "INV-PAID"));
        vars.put("amountFormatted", formatPaise(payload.get("amountPaise")));
        vars.put("description", payload.getOrDefault("description", "Fitness Membership Payment"));

        sendNotification(userId, "payment-receipt", "Payment Receipt", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handlePaymentFailed(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceNumber", payload.getOrDefault("invoiceNumber", "INV-FAILED"));
        vars.put("amountFormatted", formatPaise(payload.get("amountPaise")));
        vars.put("errorMessage", payload.getOrDefault("errorMessage", "Payment authorization failed"));

        sendNotification(userId, "payment-failed", "Payment Failed", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handleRefundPendingApproval(EventEnvelope envelope, Map<String, Object> payload) {
        List<NotificationRecipientDao.RecipientInfo> admins = recipientDao.findAdminsWithPermission("REFUND_APPROVE");
        for (NotificationRecipientDao.RecipientInfo admin : admins) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("refundId", payload.getOrDefault("refundId", "N/A"));
            vars.put("amountFormatted", formatPaise(payload.get("amountPaise")));
            vars.put("reason", payload.getOrDefault("reason", "Membership Cancellation Proration"));

            sendToRecipient(admin, "refund-needs-approval", "Action Required: Refund Pending Approval", Category.TRANSACTIONAL, vars, envelope.eventId());
        }
    }

    private void handleRefundIssued(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountFormatted", formatPaise(payload.get("amountPaise")));
        vars.put("reason", payload.getOrDefault("reason", "Membership Plan Adjustment"));

        sendNotification(userId, "refund-issued", "Refund Processed", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handlePayoutApproved(EventEnvelope envelope, Map<String, Object> payload) {
        Long userId = getLong(payload, "userId");
        if (userId == null) {
            userId = getLong(payload, "trainerUserId");
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("periodStart", payload.getOrDefault("periodStart", ""));
        vars.put("periodEnd", payload.getOrDefault("periodEnd", ""));
        vars.put("amountFormatted", formatPaise(payload.get("amountPaise")));

        sendNotification(userId, "payout-approved", "Trainer Payout Approved", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void handleFeedbackPainReported(EventEnvelope envelope, Map<String, Object> payload) {
        Long trainerUserId = getLong(payload, "trainerUserId");
        if (trainerUserId == null) {
            Long sessionId = getLong(payload, "sessionId");
            trainerUserId = recipientDao.findTrainerUserIdForSession(sessionId).orElse(null);
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("className", payload.getOrDefault("className", "Fitness Class"));
        vars.put("sessionDate", payload.getOrDefault("sessionDate", ""));
        vars.put("areaCode", payload.getOrDefault("areaCode", "LOWER_BACK"));

        sendNotification(trainerUserId, "trainer-pain-flag", "Member Pain Feedback Alert", Category.TRANSACTIONAL, vars, envelope.eventId());
    }

    private void sendNotification(Long userId, String template, String subject, Category category, Map<String, Object> variables, Long eventId) {
        sendNotificationInternal(userId, template, subject, category, variables, eventId, false);
    }

    private void sendNotificationInternal(Long userId, String template, String subject, Category category, Map<String, Object> variables, Long eventId, boolean allowSuspended) {
        if (userId == null) {
            log.warn("Cannot send email template {}: userId is null", template);
            return;
        }

        Optional<NotificationRecipientDao.RecipientInfo> recipientOpt = recipientDao.findById(userId);
        if (recipientOpt.isEmpty()) {
            log.warn("Recipient not found in database for user id {}", userId);
            return;
        }

        NotificationRecipientDao.RecipientInfo recipient = recipientOpt.get();

        if ("DELETED".equalsIgnoreCase(recipient.status())) {
            log.info("Skipping email template {} for DELETED user id {}", template, userId);
            return;
        }

        if ("SUSPENDED".equalsIgnoreCase(recipient.status()) && !allowSuspended) {
            log.info("Skipping email template {} for SUSPENDED user id {}", template, userId);
            return;
        }

        sendToRecipient(recipient, template, subject, category, variables, eventId);
    }

    private void sendToRecipient(NotificationRecipientDao.RecipientInfo recipient, String template, String subject, Category category, Map<String, Object> variables, Long eventId) {
        // Preference check
        if (category == Category.BOOKINGS && !recipient.emailBookings()) {
            log.info("User id {} opted out of BOOKINGS emails; skipping template {}", recipient.id(), template);
            return;
        }
        if (category == Category.REMINDERS && !recipient.emailReminders()) {
            log.info("User id {} opted out of REMINDERS emails; skipping template {}", recipient.id(), template);
            return;
        }
        if (category == Category.MARKETING && !recipient.emailMarketing()) {
            log.info("User id {} opted out of MARKETING emails; skipping template {}", recipient.id(), template);
            return;
        }

        // Check if already sent in Postgres notification_log to avoid duplicate sends
        if (eventId != null && notificationLogDao.hasAlreadySent(recipient.id(), template, eventId)) {
            log.info("Email for user id {}, template {}, event id {} was already sent; skipping duplicate",
                    recipient.id(), template, eventId);
            return;
        }

        Map<String, Object> vars = new HashMap<>(variables != null ? variables : Map.of());
        vars.put("firstName", recipient.firstName());
        if (category == Category.MARKETING) {
            vars.put("unsubscribeUrl", emailService.getFrontendBaseUrl() + "/me/notifications/unsubscribe");
        }

        Long logId = notificationLogDao.recordPending(recipient.id(), "EMAIL", template, eventId, clock.instant());
        try {
            boolean sent = emailService.sendEmail(recipient.id(), recipient.email(), template, subject, vars);
            if (sent) {
                notificationLogDao.recordSuccess(logId, clock.instant());
            } else {
                notificationLogDao.recordFailure(logId, "Rate limit exceeded or dropped", clock.instant());
            }
        } catch (Exception e) {
            notificationLogDao.recordFailure(logId, e.getMessage(), clock.instant());
            throw e;
        }
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatPaise(Object val) {
        if (val == null) return MoneyFormatter.formatPaise(0L);
        if (val instanceof Number n) return MoneyFormatter.formatPaise(n.longValue());
        try {
            return MoneyFormatter.formatPaise(Long.parseLong(val.toString()));
        } catch (NumberFormatException e) {
            return MoneyFormatter.formatPaise(0L);
        }
    }

    private String formatTime(Object val) {
        if (val == null) return "";
        if (val instanceof Instant inst) return emailService.formatInstant(inst);
        try {
            return emailService.formatInstant(Instant.parse(val.toString()));
        } catch (Exception e) {
            return val.toString();
        }
    }
}
