-- ==============================================================================
-- FitFlow Repeatable Dev Seed Data (MySQL 8)
-- Location: db/seed-dev/R__dev_seed.sql (Active only in dev profile)
-- ==============================================================================

-- 1. Membership Plans
INSERT INTO membership_plans (id, code, name, price_paise, duration_days, session_quota, is_free, free_sessions, active, sort_order, version, created_at, updated_at)
VALUES
(1, 'FREE', 'Free Tier', 0, NULL, NULL, TRUE, 3, TRUE, 1, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
(2, 'BASIC', 'FitFlow Basic', 49900, 30, 8, FALSE, NULL, TRUE, 2, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
(3, 'PRO', 'FitFlow Pro', 89900, 30, 16, FALSE, NULL, TRUE, 3, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
(4, 'ELITE', 'FitFlow Elite Unlimited', 149900, 30, NULL, FALSE, NULL, TRUE, 4, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price_paise = VALUES(price_paise),
    duration_days = VALUES(duration_days),
    session_quota = VALUES(session_quota),
    is_free = VALUES(is_free),
    free_sessions = VALUES(free_sessions),
    active = VALUES(active),
    sort_order = VALUES(sort_order),
    updated_at = UTC_TIMESTAMP();
