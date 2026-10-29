-- ==============================================================================
-- FitFlow Agent Stage 9 Migration (PostgreSQL 16/17)
-- Version: V4__stage9_agent_expansion.sql
-- ==============================================================================

-- 1. Job Execution Audit Log
CREATE TABLE IF NOT EXISTS job_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    processed INT NOT NULL,
    failed INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_runs_name_started ON job_runs (name, started_at DESC);

-- 2. At-Risk Member Flags Deduplication
CREATE TABLE IF NOT EXISTS at_risk_flags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    flagged_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_at_risk_flags_member_flagged ON at_risk_flags (member_id, flagged_on DESC);

-- 3. Coach Summaries Schema Extensions
ALTER TABLE coach_summaries ADD COLUMN IF NOT EXISTS numbers_json JSONB NULL;
ALTER TABLE coach_summaries ADD COLUMN IF NOT EXISTS summary_text TEXT NULL;

-- 4. Weekly Plans Result JSON Storage
ALTER TABLE weekly_plans ADD COLUMN IF NOT EXISTS results_json JSONB NULL;
