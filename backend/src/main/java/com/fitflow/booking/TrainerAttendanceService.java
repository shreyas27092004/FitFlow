package com.fitflow.booking;

import com.fitflow.booking.dto.AttendanceRecordDto;
import com.fitflow.booking.dto.CompleteSessionRequest;
import com.fitflow.booking.dto.CompleteSessionResponse;
import com.fitflow.booking.dto.MarkAttendanceRequest;
import com.fitflow.booking.dto.MarkAttendanceResponse;
import com.fitflow.booking.entity.AttendanceRecord;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.AttendanceStatus;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.repository.AttendanceRecordRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.notification.OutboxService;
import com.fitflow.payout.entity.TrainerSessionEarnings;
import com.fitflow.payout.model.EarningsStatus;
import com.fitflow.payout.repository.TrainerSessionEarningsRepository;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainerAttendanceService {

    private final ClassSessionRepository classSessionRepository;
    private final BookingRepository bookingRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final TrainerSessionEarningsRepository earningsRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    @Transactional
    public MarkAttendanceResponse markAttendance(Long sessionId, Long trainerId, MarkAttendanceRequest request) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + sessionId));

        if (!session.getTrainerId().equals(trainerId)) {
            // Only for sessions where trainer_id = caller (else 404)
            throw ApiException.notFound("Session not found with id: " + sessionId);
        }

        Instant now = clock.instant();
        if (now.isBefore(session.getStartsAt())) {
            throw ApiException.bookingRuleViolation("Attendance cannot be marked before the session has started");
        }

        Instant markingDeadline = session.getEndsAt().plus(Duration.ofHours(24));
        if (now.isAfter(markingDeadline)) {
            throw ApiException.bookingRuleViolation("Attendance marking window has closed (cutoff is 24 hours after session end)");
        }

        for (AttendanceRecordDto recordDto : request.records()) {
            Booking booking = bookingRepository.findById(recordDto.bookingId())
                    .orElseThrow(() -> ApiException.notFound("Booking not found with id: " + recordDto.bookingId()));

            if (!booking.getSessionId().equals(sessionId)) {
                throw ApiException.validationFailed("Booking id " + recordDto.bookingId() + " does not belong to session " + sessionId);
            }

            BookingStatus newBookingStatus = (recordDto.status() == AttendanceStatus.ATTENDED)
                    ? BookingStatus.ATTENDED
                    : BookingStatus.NO_SHOW;
            booking.setStatus(newBookingStatus);
            bookingRepository.save(booking);

            AttendanceRecord record = attendanceRecordRepository.findByBookingId(booking.getId())
                    .orElseGet(() -> AttendanceRecord.builder()
                            .bookingId(booking.getId())
                            .createdAt(now)
                            .build());

            record.setMarkedBy(trainerId);
            record.setMarkedAt(now);
            record.setStatus(recordDto.status());
            attendanceRecordRepository.save(record);
        }

        // Emit attendance.marked
        Map<String, Object> outboxPayload = Map.of(
                "sessionId", sessionId,
                "trainerId", trainerId,
                "recordCount", request.records().size()
        );
        outboxService.append("session", sessionId, "attendance.marked", outboxPayload);

        log.info("Attendance marked for sessionId={} by trainerId={}, count={}",
                sessionId, trainerId, request.records().size());

        return new MarkAttendanceResponse(sessionId, request.records().size());
    }

    @Transactional
    public CompleteSessionResponse completeSession(Long sessionId, Long trainerId, CompleteSessionRequest request) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + sessionId));

        if (!session.getTrainerId().equals(trainerId)) {
            // Only for sessions where trainer_id = caller (else 404)
            throw ApiException.notFound("Session not found with id: " + sessionId);
        }

        if (session.getStatus() == ClassSession.Status.COMPLETED) {
            throw ApiException.bookingRuleViolation("Session is already completed");
        }

        if (session.getStatus() == ClassSession.Status.CANCELLED) {
            throw ApiException.bookingRuleViolation("Cannot complete a cancelled session");
        }

        Instant now = clock.instant();
        if (now.isBefore(session.getEndsAt())) {
            throw ApiException.bookingRuleViolation("Session cannot be completed before it has ended");
        }

        List<Booking> bookings = bookingRepository.findBySessionId(sessionId);
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        boolean hasAttendance = !bookingIds.isEmpty() && attendanceRecordRepository.existsByBookingIdIn(bookingIds);

        boolean noAttendeesFlag = request != null && Boolean.TRUE.equals(request.noAttendees());
        if (!hasAttendance && !noAttendeesFlag) {
            throw ApiException.bookingRuleViolation("Session requires at least one attendance record or explicit noAttendees=true");
        }

        // Check if earnings row already recorded
        if (earningsRepository.existsBySessionId(sessionId)) {
            throw ApiException.conflict("Earnings already recorded for session id: " + sessionId);
        }

        // Update session status to COMPLETED
        session.setStatus(ClassSession.Status.COMPLETED);
        classSessionRepository.saveAndFlush(session);

        // Create exactly ONE trainer_session_earnings row
        TrainerSessionEarnings earnings = TrainerSessionEarnings.builder()
                .sessionId(sessionId)
                .trainerId(trainerId)
                .amountPaise(session.getTrainerRateSnapshotPaise())
                .status(EarningsStatus.PENDING)
                .createdAt(now)
                .build();
        earningsRepository.saveAndFlush(earnings);

        // Emit attendance.marked and session.completed
        outboxService.append("session", sessionId, "attendance.marked", Map.of(
                "sessionId", sessionId,
                "trainerId", trainerId,
                "recordCount", bookings.size()
        ));

        outboxService.append("session", sessionId, "session.completed", Map.of(
                "sessionId", sessionId,
                "trainerId", trainerId,
                "amountPaise", session.getTrainerRateSnapshotPaise()
        ));

        log.info("Session id={} completed by trainerId={}, earnings recorded: {} paise",
                sessionId, trainerId, session.getTrainerRateSnapshotPaise());

        return new CompleteSessionResponse(sessionId, "COMPLETED", session.getTrainerRateSnapshotPaise());
    }
}
