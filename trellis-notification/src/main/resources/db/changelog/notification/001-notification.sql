--liquibase formatted sql

--changeset trellis:notification-001-notification
--comment: Notification aggregate (sample module). code is the public id; version is the optimistic lock and ETag.
CREATE SEQUENCE notification_id_seq INCREMENT BY 1;
CREATE TABLE notification (
    id                 BIGINT DEFAULT nextval('notification_id_seq')         PRIMARY KEY,
    code               VARCHAR(17)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    body               TEXT         NOT NULL,
    severity           VARCHAR(16)  NOT NULL,
    audience           VARCHAR(16)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    author_id          VARCHAR(200) NOT NULL,
    version            BIGINT       NOT NULL,
    publish_count      INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    first_published_at TIMESTAMPTZ,
    published_at       TIMESTAMPTZ,
    published_by       VARCHAR(200),
    archived_at        TIMESTAMPTZ,
    CONSTRAINT uq_notification_code UNIQUE (code)
);
-- Keyset order of the list API: (created_at DESC, id DESC).
CREATE INDEX ix_notification_created ON notification (created_at DESC, id DESC);
--rollback DROP TABLE notification; DROP SEQUENCE notification_id_seq;

--changeset trellis:notification-002-notification-stats
--comment: Read model fed by notification.published (Java NotificationStatsProjection); rebuildable from notification.
CREATE TABLE notification_stats (
    notification_code        VARCHAR(17) PRIMARY KEY,
    publish_count      INTEGER     NOT NULL,
    first_published_at TIMESTAMPTZ,
    last_published_at  TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE notification_stats;

--changeset trellis:notification-003-notification-stats-counter
--comment: Counters of the Python notification-stats worker when it shares this database. Same DDL as its
--comment: dev copy (workers/notification-stats/.../notification_stats_counter.dev.sql); keep both in step.
CREATE TABLE IF NOT EXISTS notification_stats_counter (
    counter_key VARCHAR(100) PRIMARY KEY,
    value       BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE notification_stats_counter;
