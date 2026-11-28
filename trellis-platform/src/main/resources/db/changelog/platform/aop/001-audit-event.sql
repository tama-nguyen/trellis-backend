--liquibase formatted sql

--changeset tam.nguyenthanh:aop-001-audit-event
--comment: Append-only audit trail written by @Audited inside the business transaction.
CREATE TABLE audit_event (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       UUID         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_type     VARCHAR(16)  NOT NULL,
    actor_id       VARCHAR(200) NOT NULL,
    action         VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(200),
    correlation_id VARCHAR(100) NOT NULL,
    trace_id       VARCHAR(32),
    CONSTRAINT uq_audit_event_event_id UNIQUE (event_id)
);
CREATE INDEX ix_audit_event_aggregate ON audit_event (aggregate_type, aggregate_id, id);
CREATE INDEX ix_audit_event_occurred ON audit_event (occurred_at);
--rollback DROP TABLE audit_event;

--changeset tam.nguyenthanh:aop-002-idempotency-record
--comment: Stored results of @Idempotent methods, keyed by (scope, key).
CREATE TABLE idempotency_record (
    scope      VARCHAR(200) NOT NULL,
    idem_key   VARCHAR(200) NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    response   TEXT,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (scope, idem_key),
    CONSTRAINT ck_idempotency_record_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);
CREATE INDEX ix_idempotency_record_expires ON idempotency_record (expires_at);
--rollback DROP TABLE idempotency_record;
