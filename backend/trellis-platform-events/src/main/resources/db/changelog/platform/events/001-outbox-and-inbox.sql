--liquibase formatted sql

--changeset trellis:events-001-outbox-event
--comment: Transactional outbox (plan 09 A.3.1). Column names stay Debezium-outbox friendly so a CDC relay can replace polling.
CREATE TABLE outbox_event (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        UUID          NOT NULL,
    event_type      VARCHAR(150)  NOT NULL,
    event_version   SMALLINT      NOT NULL DEFAULT 1,
    aggregate_type  VARCHAR(100)  NOT NULL,
    aggregate_id    VARCHAR(200)  NOT NULL,
    partition_key   VARCHAR(200)  NOT NULL,
    shard           SMALLINT      NOT NULL,
    topic           VARCHAR(200)  NOT NULL,
    payload         JSONB         NOT NULL,
    headers         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(16)   NOT NULL DEFAULT 'NEW',
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_error      VARCHAR(2000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('NEW', 'PUBLISHED', 'FAILED'))
);
-- Hot path: due rows of one shard in insertion order.
CREATE INDEX ix_outbox_event_due ON outbox_event (shard, next_attempt_at, id) WHERE status = 'NEW';
-- Head-of-key probe: is there an older unpublished row for this key?
CREATE INDEX ix_outbox_event_key_pending ON outbox_event (partition_key, id) WHERE status IN ('NEW', 'FAILED');
-- Retention sweep and age of the oldest NEW row.
CREATE INDEX ix_outbox_event_created ON outbox_event (created_at);
--rollback DROP TABLE outbox_event;

--changeset trellis:events-002-processed-event
--comment: Consumer inbox: one row per (consumer, event), inserted in the consumer's transaction.
CREATE TABLE processed_event (
    consumer_name  VARCHAR(100) NOT NULL,
    event_id       UUID         NOT NULL,
    correlation_id VARCHAR(100),
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);
CREATE INDEX ix_processed_event_at ON processed_event (processed_at);
--rollback DROP TABLE processed_event;
