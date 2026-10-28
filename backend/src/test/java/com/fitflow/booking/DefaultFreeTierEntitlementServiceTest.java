package com.fitflow.booking;

import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.ConsumedFrom;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.common.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class DefaultFreeTierEntitlementServiceTest {

    private BookingRepository bookingRepository;
    private DefaultFreeTierEntitlementService service;

    @BeforeEach
    void setUp() {
        bookingRepository = Mockito.mock(BookingRepository.class);
        service = new DefaultFreeTierEntitlementService(bookingRepository, 3);
    }

    @Test
    @DisplayName("reserve() succeeds when member has used fewer than maxFreeSessions")
    void testReserveAllowedWhenQuotaAvailable() {
        when(bookingRepository.countByMemberIdAndStatusIn(eq(100L), any()))
                .thenReturn(1L);

        Entitlement entitlement = service.reserve(100L, 50L, "key-1");
        assertNotNull(entitlement);
        assertEquals(ConsumedFrom.FREE_SESSION, entitlement.consumedFrom());
    }

    @Test
    @DisplayName("reserve() throws UPGRADE_REQUIRED when member has reached quota limit")
    void testReserveFailsWhenQuotaReached() {
        when(bookingRepository.countByMemberIdAndStatusIn(eq(100L), any()))
                .thenReturn(3L);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.reserve(100L, 50L, "key-2")
        );
        assertEquals("UPGRADE_REQUIRED", ex.getErrorCode().name());
    }

    @Test
    @DisplayName("reserve() throws UPGRADE_REQUIRED when member has exceeded quota limit")
    void testReserveFailsWhenQuotaExceeded() {
        when(bookingRepository.countByMemberIdAndStatusIn(eq(100L), any()))
                .thenReturn(4L);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.reserve(100L, 50L, "key-3")
        );
        assertEquals("UPGRADE_REQUIRED", ex.getErrorCode().name());
    }

    @Test
    @DisplayName("restore() allows member to rebook after cancellation")
    void testRestoreAllowsRebooking() {
        com.fitflow.booking.entity.Booking cancelledBooking = com.fitflow.booking.entity.Booking.builder()
                .id(99L)
                .memberId(100L)
                .status(BookingStatus.CANCELLED)
                .build();

        // 2 active bookings + 1 cancelled booking
        when(bookingRepository.countByMemberIdAndStatusIn(eq(100L), any())).thenReturn(2L);
        when(bookingRepository.findByMemberIdAndStatus(eq(100L), eq(BookingStatus.CANCELLED)))
                .thenReturn(java.util.List.of(cancelledBooking));

        // Before restore: 2 active + 1 burned = 3 => quota reached
        assertThrows(ApiException.class, () -> service.reserve(100L, 50L, "key-before-restore"));

        // After restore: booking 99 is marked restored
        service.restore(99L, "Member cancellation");

        // Now reserve succeeds
        Entitlement entitlement = service.reserve(100L, 50L, "key-after-restore");
        assertNotNull(entitlement);
        assertEquals(ConsumedFrom.FREE_SESSION, entitlement.consumedFrom());
    }
}
