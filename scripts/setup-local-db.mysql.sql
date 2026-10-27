-- ==============================================================================
-- FitFlow Local MySQL Setup Script
-- Run as root: mysql -u root -p < scripts/setup-local-db.mysql.sql
-- ==============================================================================

-- Create main and test databases
CREATE DATABASE IF NOT EXISTS fitflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS fitflow_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Create application user with restricted privileges (No global GRANT ALL)
-- Replace 'fitflow_pass' with your secure local development password
CREATE USER IF NOT EXISTS 'fitflow_app'@'localhost' IDENTIFIED BY 'fitflow_pass';
ALTER USER 'fitflow_app'@'localhost' IDENTIFIED BY 'fitflow_pass';

CREATE USER IF NOT EXISTS 'fitflow_app'@'127.0.0.1' IDENTIFIED BY 'fitflow_pass';
ALTER USER 'fitflow_app'@'127.0.0.1' IDENTIFIED BY 'fitflow_pass';

-- Grant permissions ONLY on fitflow and fitflow_test
GRANT ALL PRIVILEGES ON fitflow.* TO 'fitflow_app'@'localhost';
GRANT ALL PRIVILEGES ON fitflow.* TO 'fitflow_app'@'127.0.0.1';

GRANT ALL PRIVILEGES ON fitflow_test.* TO 'fitflow_app'@'localhost';
GRANT ALL PRIVILEGES ON fitflow_test.* TO 'fitflow_app'@'127.0.0.1';

FLUSH PRIVILEGES;
