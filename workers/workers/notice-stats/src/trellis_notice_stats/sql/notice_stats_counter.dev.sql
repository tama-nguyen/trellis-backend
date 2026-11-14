-- DEV/TEST ONLY. A real deployment creates this table with its migration tool (the backend's
-- Liquibase changelog, if the worker shares the backend database).
CREATE TABLE IF NOT EXISTS notice_stats_counter (
    counter_key VARCHAR(100) PRIMARY KEY,
    value       BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
