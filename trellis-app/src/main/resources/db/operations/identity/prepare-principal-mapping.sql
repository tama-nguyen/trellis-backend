-- Operator-only staging; not included in Liquibase and never run at application startup.
-- Run inside an explicit transaction. All staging data disappears at commit/rollback.
CREATE TEMPORARY TABLE principal_migration_direction (
    singleton BOOLEAN PRIMARY KEY CHECK (singleton),
    reverse BOOLEAN NOT NULL
) ON COMMIT DROP;
INSERT INTO principal_migration_direction VALUES (TRUE, FALSE);

CREATE TEMPORARY TABLE principal_identity_map (
    mapping_key UUID PRIMARY KEY,
    subject_type VARCHAR(16) NOT NULL CHECK (subject_type IN ('USER', 'SERVICE')),
    legacy_id VARCHAR(200) NOT NULL CHECK (btrim(legacy_id) <> ''),
    issuer TEXT NOT NULL CHECK (btrim(issuer) <> ''),
    external_subject TEXT NOT NULL CHECK (btrim(external_subject) <> ''),
    encoded_id VARCHAR(200) NOT NULL,
    provenance_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (legacy_id = external_subject),
    CHECK ((subject_type = 'USER' AND encoded_id ~ '^oidc-user:[0-9a-f]{64}$')
        OR (subject_type = 'SERVICE' AND encoded_id ~ '^oidc-service:[0-9a-f]{64}$')),
    UNIQUE (subject_type, issuer, external_subject),
    UNIQUE (subject_type, encoded_id)
) ON COMMIT DROP;

-- Select exact rows after provenance review, rather than updating everything with a matching ID.
CREATE TEMPORARY TABLE principal_grant_map (
    grant_id BIGINT PRIMARY KEY,
    mapping_key UUID NOT NULL REFERENCES principal_identity_map(mapping_key)
) ON COMMIT DROP;

CREATE TEMPORARY TABLE principal_course_map (
    course_id UUID PRIMARY KEY,
    mapping_key UUID NOT NULL REFERENCES principal_identity_map(mapping_key)
) ON COMMIT DROP;

CREATE TEMPORARY TABLE principal_notification_map (
    notification_id UUID PRIMARY KEY,
    author_mapping_key UUID REFERENCES principal_identity_map(mapping_key),
    publisher_mapping_key UUID REFERENCES principal_identity_map(mapping_key),
    CHECK (author_mapping_key IS NOT NULL OR publisher_mapping_key IS NOT NULL)
) ON COMMIT DROP;
