-- Stage 9: Free Session Grants Ledger Table (MySQL)
CREATE TABLE free_session_grants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    sessions INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    granted_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_free_session_grants_member_created (member_id, created_at),
    CONSTRAINT fk_free_session_grants_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
