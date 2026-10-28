-- ==============================================================================
-- FitFlow Stage 3 Migration (MySQL 8)
-- Version: V2__user_notification_prefs.sql
-- ==============================================================================

ALTER TABLE users ADD COLUMN notification_prefs JSON NULL;
ALTER TABLE users MODIFY full_name VARCHAR(150) NULL;
