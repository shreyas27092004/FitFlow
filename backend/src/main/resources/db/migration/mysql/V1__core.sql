-- ==============================================================================
-- FitFlow Core Transactional Database Migration (MySQL 8)
-- Version: V1__core.sql
-- ==============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1. Users and Authentication
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NULL,
    date_of_birth DATE NULL,
    status ENUM('ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role ENUM('MEMBER', 'TRAINER', 'ADMIN') NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_permissions (
    user_id BIGINT NOT NULL,
    permission VARCHAR(40) NOT NULL,
    granted_by BIGINT NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, permission),
    CONSTRAINT fk_admin_perm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_perm_granter FOREIGN KEY (granted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    family_id CHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_id BIGINT NULL,
    user_agent VARCHAR(200) NULL,
    ip_hash CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_pw_reset_token_hash (token_hash),
    CONSTRAINT fk_pw_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Trainer Profiles
CREATE TABLE trainer_profiles (
    user_id BIGINT NOT NULL PRIMARY KEY,
    bio TEXT NULL,
    specialties JSON NULL,
    certifications JSON NULL,
    rate_per_session_paise BIGINT NOT NULL DEFAULT 0,
    availability JSON NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_trainer_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Classes and Sessions
CREATE TABLE fitness_classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    category VARCHAR(40) NOT NULL,
    duration_minutes INT NOT NULL,
    intensity ENUM('LOW', 'MODERATE', 'HIGH') NOT NULL,
    impact_level ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL,
    met_value DECIMAL(4,1) NOT NULL,
    contraindication_tags JSON NULL,
    min_age INT NOT NULL DEFAULT 14,
    default_capacity INT NOT NULL DEFAULT 20,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    UNIQUE KEY uk_fitness_classes_name (name),
    CONSTRAINT fk_fitness_classes_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    trainer_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    capacity INT NOT NULL,
    booked_count INT NOT NULL DEFAULT 0,
    room VARCHAR(60) NOT NULL,
    status ENUM('SCHEDULED', 'CANCELLED', 'COMPLETED') NOT NULL DEFAULT 'SCHEDULED',
    series_id CHAR(36) NULL,
    trainer_rate_snapshot_paise BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    INDEX idx_sessions_starts_at (starts_at),
    INDEX idx_sessions_trainer_starts (trainer_id, starts_at),
    CONSTRAINT fk_class_sessions_class FOREIGN KEY (class_id) REFERENCES fitness_classes(id),
    CONSTRAINT fk_class_sessions_trainer FOREIGN KEY (trainer_id) REFERENCES users(id),
    CONSTRAINT chk_sessions_booked_count CHECK (booked_count >= 0 AND booked_count <= capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Bookings, Waitlists, Attendance
CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status ENUM('CONFIRMED', 'CANCELLED', 'ATTENDED', 'NO_SHOW') NOT NULL DEFAULT 'CONFIRMED',
    consumed_from ENUM('FREE_SESSION', 'PLAN_SESSION') NOT NULL,
    membership_id BIGINT NULL,
    idempotency_key VARCHAR(64) NULL,
    booked_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    active_flag TINYINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    UNIQUE KEY uk_bookings_session_member_active (session_id, member_id, active_flag),
    UNIQUE KEY uk_bookings_member_idempotency (member_id, idempotency_key),
    CONSTRAINT fk_bookings_session FOREIGN KEY (session_id) REFERENCES class_sessions(id),
    CONSTRAINT fk_bookings_member FOREIGN KEY (member_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE waitlist_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status ENUM('WAITING', 'PROMOTED', 'LEFT') NOT NULL DEFAULT 'WAITING',
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_waitlist_session_member (session_id, member_id),
    INDEX idx_waitlist_created_id (created_at, id),
    CONSTRAINT fk_waitlist_session FOREIGN KEY (session_id) REFERENCES class_sessions(id),
    CONSTRAINT fk_waitlist_member FOREIGN KEY (member_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    marked_by BIGINT NOT NULL,
    marked_at DATETIME(6) NOT NULL,
    status ENUM('ATTENDED', 'NO_SHOW') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_attendance_booking (booking_id),
    CONSTRAINT fk_attendance_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_attendance_marker FOREIGN KEY (marked_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Memberships and Plans
CREATE TABLE membership_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_paise BIGINT NOT NULL,
    duration_days INT NULL,
    session_quota INT NULL,
    is_free BOOLEAN NOT NULL DEFAULT FALSE,
    free_sessions INT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    UNIQUE KEY uk_membership_plans_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    started_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NULL,
    sessions_total INT NULL,
    sessions_used INT NOT NULL DEFAULT 0,
    amount_paid_paise BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_memberships_member FOREIGN KEY (member_id) REFERENCES users(id),
    CONSTRAINT fk_memberships_plan FOREIGN KEY (plan_id) REFERENCES membership_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE membership_changes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_id BIGINT NOT NULL,
    from_plan_id BIGINT NOT NULL,
    to_plan_id BIGINT NOT NULL,
    credit_paise BIGINT NOT NULL DEFAULT 0,
    amount_due_paise BIGINT NOT NULL DEFAULT 0,
    refund_paise BIGINT NOT NULL DEFAULT 0,
    policy_name VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_mem_changes_membership FOREIGN KEY (membership_id) REFERENCES memberships(id),
    CONSTRAINT fk_mem_changes_from_plan FOREIGN KEY (from_plan_id) REFERENCES membership_plans(id),
    CONSTRAINT fk_mem_changes_to_plan FOREIGN KEY (to_plan_id) REFERENCES membership_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Payments, Refunds, Invoices
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    membership_id BIGINT NULL,
    amount_paise BIGINT NOT NULL,
    status ENUM('PENDING', 'SUCCEEDED', 'FAILED') NOT NULL,
    gateway_ref VARCHAR(80) NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    failure_reason VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_payments_member_idempotency (member_id, idempotency_key),
    CONSTRAINT fk_payments_member FOREIGN KEY (member_id) REFERENCES users(id),
    CONSTRAINT fk_payments_membership FOREIGN KEY (membership_id) REFERENCES memberships(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    amount_paise BIGINT NOT NULL,
    status ENUM('PENDING_APPROVAL', 'APPROVED', 'PAID', 'REJECTED') NOT NULL,
    approved_by BIGINT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_refunds_payment_idempotency (payment_id, idempotency_key),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_refunds_approver FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    invoice_number VARCHAR(60) NOT NULL,
    total_paise BIGINT NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_invoices_payment (payment_id),
    UNIQUE KEY uk_invoices_number (invoice_number),
    CONSTRAINT fk_invoices_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Payouts and Earnings
CREATE TABLE trainer_payouts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status ENUM('DRAFT', 'APPROVED', 'PAID') NOT NULL DEFAULT 'DRAFT',
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_paise BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    approved_by BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_payouts_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_payouts_approver FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trainer_session_earnings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    trainer_id BIGINT NOT NULL,
    amount_paise BIGINT NOT NULL,
    status ENUM('PENDING', 'IN_BATCH', 'PAID') NOT NULL DEFAULT 'PENDING',
    payout_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_session_earnings_session (session_id),
    CONSTRAINT fk_session_earnings_session FOREIGN KEY (session_id) REFERENCES class_sessions(id),
    CONSTRAINT fk_session_earnings_trainer FOREIGN KEY (trainer_id) REFERENCES users(id),
    CONSTRAINT fk_session_earnings_payout FOREIGN KEY (payout_id) REFERENCES trainer_payouts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Outbox Pattern
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status ENUM('PENDING', 'SENT', 'FAILED') NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(300) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_outbox_status_next (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Wellness, Encrypted Health Data & Feedback
CREATE TABLE member_health_profiles (
    member_id BIGINT NOT NULL PRIMARY KEY,
    data_encrypted MEDIUMBLOB NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_health_profile_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE health_consents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    purpose VARCHAR(80) NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_health_consents_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE body_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    recorded_on DATE NOT NULL,
    weight_kg_enc VARBINARY(255) NOT NULL,
    height_cm_enc VARBINARY(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_body_metrics_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE progress_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    log_date DATE NOT NULL,
    kind VARCHAR(30) NOT NULL,
    value_enc VARBINARY(255) NULL,
    note_enc VARBINARY(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_progress_logs_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    felt_pain BOOLEAN NOT NULL DEFAULT FALSE,
    pain_area_code VARCHAR(30) NULL,
    comment_enc VARBINARY(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_feedback_booking (booking_id),
    CONSTRAINT fk_feedback_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_feedback_member FOREIGN KEY (member_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Audit, Secrets, Settings
CREATE TABLE admin_action_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    ip_hash CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_admin_log_admin FOREIGN KEY (admin_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE app_secrets (
    name VARCHAR(100) NOT NULL PRIMARY KEY,
    encrypted_value TEXT NOT NULL,
    key_version INT NOT NULL DEFAULT 1,
    updated_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_app_secrets_user FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE app_settings (
    name VARCHAR(100) NOT NULL PRIMARY KEY,
    value VARCHAR(500) NOT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_app_settings_user FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
