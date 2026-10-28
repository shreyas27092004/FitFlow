-- Stage 7: Health Access Log, Restriction Proposals, and Body Metrics Schema Updates

-- 1. Health Access Log
CREATE TABLE health_access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action ENUM('READ','WRITE','EXPORT','DELETE') NOT NULL,
    purpose VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_health_access_member (member_id, created_at),
    CONSTRAINT fk_health_access_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Restriction Proposals (pain-feedback generated injury suggestions)
CREATE TABLE restriction_proposals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    area VARCHAR(50) NOT NULL,
    suggested_severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    INDEX idx_restriction_member_status (member_id, status),
    CONSTRAINT fk_restriction_proposals_member FOREIGN KEY (member_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Modify body_metrics to allow optional height_cm
ALTER TABLE body_metrics MODIFY COLUMN height_cm_enc VARBINARY(255) NULL;
