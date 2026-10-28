-- ==============================================================================
-- FitFlow Idempotency Records Migration (MySQL 8)
-- Version: V3__idempotency_records.sql
-- ==============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_idempotency_user_key (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
