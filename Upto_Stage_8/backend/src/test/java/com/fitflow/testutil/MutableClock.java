package com.fitflow.testutil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneOffset.UTC);
    }

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = initialInstant;
        this.zone = zone;
    }

    public static MutableClock ofUtc(Instant initial) {
        return new MutableClock(initial, ZoneOffset.UTC);
    }

    public void advance(Duration duration) {
        this.currentInstant = this.currentInstant.plus(duration);
    }

    public void setInstant(Instant instant) {
        this.currentInstant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.currentInstant, zone);
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
