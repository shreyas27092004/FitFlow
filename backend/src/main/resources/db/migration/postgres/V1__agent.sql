-- ==============================================================================
-- FitFlow Agent & Memory Database Migration (PostgreSQL 16/17)
-- Version: V1__agent.sql
-- ==============================================================================

-- 1. Agent Conversations
CREATE TABLE agent_conversations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversations_user ON agent_conversations (user_id);

-- 2. Agent Messages
CREATE TABLE agent_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(100) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversations(id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_conversation ON agent_messages (conversation_id);

-- 3. Agent Long-term Memory
CREATE TABLE agent_memory (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    key TEXT NOT NULL,
    value JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_memory_user_key UNIQUE (user_id, key)
);

-- 4. Agent Action Audit Log
CREATE TABLE agent_action_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    agent_name VARCHAR(50) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    args_redacted JSONB NOT NULL,
    result_status VARCHAR(30) NOT NULL,
    error TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_action_audit_user ON agent_action_audit (user_id);

-- 5. Pending Actions (Human-in-the-loop)
CREATE TABLE pending_actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    args_json JSONB NOT NULL,
    summary TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pending_actions_user_status ON pending_actions (user_id, status);

-- 6. Personalized Nutrition & Weekly Fitness Plans
CREATE TABLE nutrition_plans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inputs_coded JSONB NOT NULL,
    result JSONB NOT NULL
);

CREATE INDEX idx_nutrition_plans_member ON nutrition_plans (member_id);

CREATE TABLE weekly_plans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    plan JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_weekly_plans_member_week ON weekly_plans (member_id, week_start);

CREATE TABLE coach_summaries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    summary JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coach_summaries_member ON coach_summaries (member_id);

-- 7. Notification Logs
CREATE TABLE notification_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    template VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_log_user ON notification_log (user_id);
