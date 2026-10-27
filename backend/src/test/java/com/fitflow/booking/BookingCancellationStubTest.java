package com.fitflow.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingCancellationStubTest {

    private final BookingCancellationStub stub = new BookingCancellationStub();

    @Test
    @DisplayName("Stub Contract: cancelFutureBookingsForUser returns 0 cancelled bookings for Stage 3")
    void testCancelFutureBookingsReturnsZero() {
        int cancelled = stub.cancelFutureBookingsForUser(100L);
        assertEquals(0, cancelled);
    }

    @Test
    @DisplayName("Stub Contract: accepts null or arbitrary user ID without throwing")
    void testAcceptsArbitraryUserId() {
        assertEquals(0, stub.cancelFutureBookingsForUser(null));
        assertEquals(0, stub.cancelFutureBookingsForUser(999999L));
    }
}
