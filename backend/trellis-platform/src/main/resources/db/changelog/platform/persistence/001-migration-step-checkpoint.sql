--liquibase formatted sql

--changeset trellis:persistence-001-migration-step-checkpoint
--comment: Checkpoints of MigrationStep backfills (one row per step).
CREATE TABLE migration_step_checkpoint (
    step_id     VARCHAR(200)  NOT NULL PRIMARY KEY,
    status      VARCHAR(16)   NOT NULL,
    blocking    BOOLEAN       NOT NULL,
    checkpoint  TEXT,
    processed   BIGINT        NOT NULL DEFAULT 0,
    attempts    INT           NOT NULL DEFAULT 0,
    last_error  VARCHAR(2000),
    started_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    finished_at TIMESTAMPTZ,
    CONSTRAINT ck_migration_step_checkpoint_status CHECK (status IN ('RUNNING', 'DONE', 'FAILED'))
);
--rollback DROP TABLE migration_step_checkpoint;
