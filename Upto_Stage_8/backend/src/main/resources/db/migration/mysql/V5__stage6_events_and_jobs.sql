-- ==============================================================================
-- FitFlow MySQL Schema Migration (Stage 6)
-- Version: V2__stage6_events_and_jobs.sql
-- ==============================================================================

-- 1. Sensitive payload support in outbox_events
ALTER TABLE outbox_events ADD COLUMN payload_secret TEXT NULL;

-- 2. Consumer Idempotency Tracking
CREATE TABLE processed_events (
    consumer VARCHAR(60) NOT NULL,
    event_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (consumer, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Scheduled Booking Reminders Tracking
CREATE TABLE booking_reminders (
    booking_id BIGINT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_booking_reminders_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Scheduled Membership Notices Tracking
CREATE TABLE membership_notices (
    membership_id BIGINT NOT NULL,
    kind VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (membership_id, kind),
    CONSTRAINT fk_membership_notices_membership FOREIGN KEY (membership_id) REFERENCES memberships(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
