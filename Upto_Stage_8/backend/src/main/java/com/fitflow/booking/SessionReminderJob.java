package com.fitflow.booking;

import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.entity.BookingReminder;
import com.fitflow.booking.repository.BookingReminderRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.job.JobLockRepository;
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
@Component("sessionReminderJob")
@RequiredArgsConstructor
public class SessionReminderJob {

    private static final String JOB_NAME = "session_reminder_job";

    private final BookingRepository bookingRepository;
    private final BookingReminderRepository bookingReminderRepository;
    private final OutboxService outboxService;
    private final JobLockRepository jobLockRepository;
    private final Clock clock;

    private final String instanceId = UUID.randomUUID().toString();

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void runScheduled() {
        processReminders();
    }

    @Transactional
    public int processReminders() {
        Instant now = clock.instant();
        Instant expiredBefore = now.minusSeconds(300);

        int acquired = 0;
        try {
            acquired = jobLockRepository.acquireLock(JOB_NAME, now, instanceId, expiredBefore);
        } catch (Exception e) {
            log.warn("JobLock acquire failed for {}: {}", JOB_NAME, e.getMessage());
        }

        if (acquired == 0) {
            log.debug("Session reminder job lock held by another instance. Skipping.");
            return 0;
        }

        try {
            Instant windowStart = now.plus(Duration.ofHours(23));
            Instant windowEnd = now.plus(Duration.ofHours(25));

            List<Booking> bookings = bookingRepository.findConfirmedBookingsNeedingReminder(windowStart, windowEnd);
            log.info("Found {} bookings needing reminders between {} and {}", bookings.size(), windowStart, windowEnd);

            int count = 0;
            for (Booking booking : bookings) {
                // Double check if already reminded
                if (bookingReminderRepository.existsById(booking.getId())) {
                    continue;
                }

                BookingReminder reminder = BookingReminder.builder()
                        .bookingId(booking.getId())
                        .createdAt(now)
                        .build();
                bookingReminderRepository.save(reminder);

                outboxService.append(
                        "BOOKING",
                        booking.getId(),
                        "session.reminder",
                        Map.of(
                                "bookingId", booking.getId(),
                                "userId", booking.getMemberId(),
                                "sessionId", booking.getSessionId()
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
