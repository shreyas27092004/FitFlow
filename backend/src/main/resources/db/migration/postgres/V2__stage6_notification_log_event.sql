-- ==============================================================================
-- FitFlow PostgreSQL Schema Migration (Stage 6)
-- Version: V2__stage6_notification_log_event.sql
-- ==============================================================================

ALTER TABLE notification_log ADD COLUMN event_id BIGINT NULL;
ALTER TABLE notification_log ADD COLUMN updated_at TIMESTAMPTZ NULL;

CREATE INDEX idx_notification_log_event ON notification_log (event_id);
