-- DEV/TEST ONLY. The Java Liquibase changelog owns the real processed_event table in every
-- deployed environment; this copy exists so Python workers and their integration tests can run
-- against an empty Postgres. Keep it identical to plan/09 A.3.1 (and to the changelog).
CREATE TABLE IF NOT EXISTS processed_event (
    consumer_name  VARCHAR(100) NOT NULL,
    event_id       UUID         NOT NULL,
    correlation_id VARCHAR(100),
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
CREATE INDEX IF NOT EXISTS ix_processed_event_at ON processed_event (processed_at);
