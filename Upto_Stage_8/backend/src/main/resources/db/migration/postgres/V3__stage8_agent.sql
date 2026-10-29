-- ==============================================================================
-- FitFlow Agent Stage 8 Migration (PostgreSQL 16/17)
-- Version: V3__stage8_agent.sql
-- ==============================================================================

-- 1. Agent Daily Usage Budget Table
CREATE TABLE IF NOT EXISTS agent_usage (
    user_id BIGINT NOT NULL,
    day DATE NOT NULL,
    calls INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX IF NOT EXISTS idx_agent_usage_day ON agent_usage (day);

-- 2. Pending Actions Result JSON Storage
ALTER TABLE pending_actions ADD COLUMN IF NOT EXISTS result_json JSONB NULL;
