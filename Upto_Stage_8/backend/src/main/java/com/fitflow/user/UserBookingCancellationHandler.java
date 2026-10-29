package com.fitflow.user;

public interface UserBookingCancellationHandler {

    int cancelFutureBookingsForUser(Long userId);
}
