package com.fitflow.booking;

import com.fitflow.booking.model.ConsumedFrom;

public record Entitlement(
        ConsumedFrom consumedFrom,
        Long membershipId
) {
}
