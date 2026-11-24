--liquibase formatted sql

--changeset trellis:security-006-outcome-audit
CREATE TABLE security_event (
 id UUID PRIMARY KEY,
 occurred_at TIMESTAMPTZ NOT NULL,
 actor_type VARCHAR(16) NOT NULL,
 actor_id VARCHAR(200) NOT NULL,
 action VARCHAR(60) NOT NULL,
 outcome VARCHAR(16) NOT NULL,
 error_code VARCHAR(150),
 correlation_id VARCHAR(100) NOT NULL
);
CREATE INDEX ix_security_event_occurred ON security_event (occurred_at);
CREATE INDEX ix_security_event_actor ON security_event (actor_type,actor_id,occurred_at);
--rollback DROP TABLE security_event;
