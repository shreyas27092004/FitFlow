-- ==============================================================================
-- FitFlow Stage 5 Migration: Payment Gateway, Membership Lifecycle & Refunds
-- Location: db/migration/mysql/V4__stage5_payment_membership.sql
-- ==============================================================================

-- 1. Pending Membership Changes (for two-phase subscribe/change workflows)
CREATE TABLE pending_membership_changes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL UNIQUE,
    kind ENUM('SUBSCRIBE', 'CHANGE') NOT NULL,
    target_plan_id BIGINT NOT NULL,
    credit_paise BIGINT NOT NULL DEFAULT 0,
    amount_due_paise BIGINT NOT NULL DEFAULT 0,
    snapshot_json JSON NOT NULL,
    status ENUM('PENDING', 'APPLIED', 'EXPIRED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pmc_member FOREIGN KEY (member_id) REFERENCES users(id),
    CONSTRAINT fk_pmc_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_pmc_plan FOREIGN KEY (target_plan_id) REFERENCES membership_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Alter Payments for Two-Phase Gateway Orders
ALTER TABLE payments
    ADD COLUMN gateway VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    ADD COLUMN gateway_order_id VARCHAR(80) NULL,
    ADD COLUMN gateway_payment_id VARCHAR(80) NULL,
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'INR',
    ADD INDEX idx_payments_gateway_order (gateway_order_id);

-- 3. Alter Refunds for Gateway Refunds and Rejections
ALTER TABLE refunds
    ADD COLUMN gateway_refund_id VARCHAR(80) NULL,
    ADD COLUMN gateway VARCHAR(20) NULL,
    ADD COLUMN failure_reason VARCHAR(200) NULL,
    MODIFY COLUMN status ENUM('PENDING_APPROVAL', 'APPROVED', 'PAID', 'REJECTED', 'FAILED') NOT NULL;

-- 4. Refund Allocations (Multi-payment chain allocations)
CREATE TABLE refund_allocations (
    refund_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount_paise BIGINT NOT NULL,
    PRIMARY KEY (refund_id, payment_id),
    CONSTRAINT fk_ra_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
    CONSTRAINT fk_ra_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Payment Webhook Events (for Stage 5B Razorpay webhooks)
CREATE TABLE payment_webhook_events (
    event_id VARCHAR(80) PRIMARY KEY,
    gateway VARCHAR(20) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    status ENUM('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED') NOT NULL,
    error VARCHAR(300) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Job Locks (for single-instance safe scheduled jobs)
CREATE TABLE job_locks (
    job_name VARCHAR(50) PRIMARY KEY,
    locked_at DATETIME(6) NOT NULL,
    locked_by VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Add restored_at column to Bookings table (guarantees restore happens at most once per booking)
ALTER TABLE bookings
    ADD COLUMN restored_at DATETIME(6) NULL;

-- 8. Seed baseline membership plans if missing
INSERT IGNORE INTO membership_plans (id, code, name, price_paise, duration_days, session_quota, is_free, free_sessions, active, sort_order, version, created_at, updated_at)
VALUES
(1, 'FREE', 'Free Tier', 0, NULL, NULL, TRUE, 3, TRUE, 1, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
(2, 'BASIC', 'FitFlow Basic', 49900, 30, 8, FALSE, NULL, TRUE, 2, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
(3, 'PRO', 'FitFlow Pro', 89900, 30, 16, FALSE, NULL, TRUE, 3, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
(4, 'ELITE', 'FitFlow Elite Unlimited', 149900, 30, NULL, FALSE, NULL, TRUE, 4, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP());

-- 9. Seed default app_settings for refund and payments
INSERT IGNORE INTO app_settings (name, value, created_at, updated_at)
VALUES
('refund.policy', 'MIN_RATIO_V1', UTC_TIMESTAMP(), UTC_TIMESTAMP()),
('refund.cancel-fee-percent', '0', UTC_TIMESTAMP(), UTC_TIMESTAMP()),
('refund.auto-approve-limit-paise', '100000', UTC_TIMESTAMP(), UTC_TIMESTAMP()),
('payments.gateway', 'MOCK', UTC_TIMESTAMP(), UTC_TIMESTAMP());
