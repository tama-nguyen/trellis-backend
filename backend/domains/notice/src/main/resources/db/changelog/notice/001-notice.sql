--liquibase formatted sql

--changeset trellis:notice-001-notice
--comment: Notice aggregate (sample module). code is the public id; version is the optimistic lock and ETag.
CREATE TABLE notice (
    id                 UUID         PRIMARY KEY,
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
    CONSTRAINT uq_notice_code UNIQUE (code),
    CONSTRAINT ck_notice_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_notice_audience CHECK (audience IN ('ALL', 'STAFF')),
    CONSTRAINT ck_notice_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_notice_version CHECK (version >= 1),
    CONSTRAINT ck_notice_published CHECK (status = 'DRAFT' OR published_at IS NOT NULL)
);
-- Keyset order of the list API: (created_at DESC, id DESC).
CREATE INDEX ix_notice_created ON notice (created_at DESC, id DESC);
--rollback DROP TABLE notice;

--changeset trellis:notice-002-notice-stats
--comment: Read model fed by notice.published (Java NoticeStatsProjection); rebuildable from notice.
CREATE TABLE notice_stats (
    notice_code        VARCHAR(17) PRIMARY KEY,
    publish_count      INTEGER     NOT NULL,
    first_published_at TIMESTAMPTZ,
    last_published_at  TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL
);
--rollback DROP TABLE notice_stats;

--changeset trellis:notice-003-notice-stats-counter
--comment: Counters of the Python notice-stats worker when it shares this database. Same DDL as its
--comment: dev copy (workers/notice-stats/.../notice_stats_counter.dev.sql); keep both in step.
CREATE TABLE IF NOT EXISTS notice_stats_counter (
    counter_key VARCHAR(100) PRIMARY KEY,
    value       BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE notice_stats_counter;
