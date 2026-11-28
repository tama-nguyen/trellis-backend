--liquibase formatted sql

--changeset tam.nguyenthanh:identity-006-mail-job
CREATE SEQUENCE identity_mail_job_id_seq INCREMENT BY 1;
CREATE TABLE identity_mail_job (
 id BIGINT DEFAULT nextval('identity_mail_job_id_seq') PRIMARY KEY,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 action_hash VARCHAR(64) NOT NULL,
 key_version VARCHAR(32) NOT NULL,
 payload TEXT NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL,
 lease_id UUID,
 lease_until TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_identity_mail_status CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
 CONSTRAINT ck_identity_mail_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_identity_mail_due ON identity_mail_job (next_attempt_at, id) WHERE completed_at IS NULL;
CREATE INDEX ix_identity_mail_cleanup ON identity_mail_job (completed_at) WHERE completed_at IS NOT NULL;
--rollback DROP TABLE identity_mail_job; DROP SEQUENCE identity_mail_job_id_seq;
