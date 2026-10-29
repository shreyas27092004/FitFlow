package com.fitflow.booking;

import com.fitflow.user.UserBookingCancellationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub per Rule R13: Future booking cancellation and credit restoration
 * will be fully implemented in Stage 4/5.
 */
@Slf4j
@Service
public class BookingCancellationStub implements UserBookingCancellationHandler {

    @Override
    public int cancelFutureBookingsForUser(Long userId) {
        log.info("BookingCancellationStub: future bookings cancellation requested for user id {}", userId);
        return 0;
    }
}
