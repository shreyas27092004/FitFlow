-- ==============================================================================
-- FitFlow Local PostgreSQL Setup Script
-- Run as postgres: psql -U postgres -f scripts/setup-local-db.pg.sql
-- ==============================================================================

-- Create application user
DO
$do$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'fitflow_app') THEN
      CREATE ROLE fitflow_app WITH LOGIN PASSWORD 'fitflow_pass';
   ELSE
      ALTER ROLE fitflow_app WITH PASSWORD 'fitflow_pass';
   END IF;
END
$do$;

-- Create databases if not exists
SELECT 'CREATE DATABASE fitflow OWNER fitflow_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fitflow')\gexec

SELECT 'CREATE DATABASE fitflow_test OWNER fitflow_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fitflow_test')\gexec

-- Grant connection and schema privileges
GRANT CONNECT ON DATABASE fitflow TO fitflow_app;
GRANT CONNECT ON DATABASE fitflow_test TO fitflow_app;

\c fitflow
GRANT ALL ON SCHEMA public TO fitflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO fitflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO fitflow_app;

\c fitflow_test
GRANT ALL ON SCHEMA public TO fitflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO fitflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO fitflow_app;
