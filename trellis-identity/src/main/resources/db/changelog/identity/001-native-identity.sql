--liquibase formatted sql

--changeset trellis:identity-001-users
--comment: Native user aggregate. email is the public login id; status gates sign-in. Plural, because "user"
--comment: is a reserved word in PostgreSQL and would need quoting in every statement.
CREATE TABLE users (
    id                UUID         PRIMARY KEY,
    email             VARCHAR(320) NOT NULL,
    display_name      VARCHAR(200) NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);
--rollback DROP TABLE users;

--changeset trellis:identity-002-user-password
--comment: Password hash kept out of the user row so reading a user never carries the credential.
CREATE TABLE user_password (
    user_id             UUID         PRIMARY KEY,
    password_hash       VARCHAR(100) NOT NULL,
    password_changed_at TIMESTAMPTZ  NOT NULL
);
--rollback DROP TABLE user_password;

--changeset trellis:identity-003-user-session
--comment: One sign-in. Only token hashes are stored; the raw tokens exist solely in the client.
--comment: VARCHAR, not CHAR: bpchar blank-pads and compares with trailing spaces ignored, which is the wrong
--comment: semantics for a digest, and Hibernate cannot validate it without a vendor annotation.
CREATE TABLE user_session (
    id                 UUID        PRIMARY KEY,
    user_id            UUID        NOT NULL,
    access_token_hash  VARCHAR(64) NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    access_expires_at  TIMESTAMPTZ NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    rotated_at         TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT uq_user_session_access_token UNIQUE (access_token_hash),
    CONSTRAINT uq_user_session_refresh_token UNIQUE (refresh_token_hash)
);
--rollback DROP TABLE user_session;

--changeset trellis:identity-004-user-action-token
--comment: Single-use email verification and password reset tokens; consumed_at makes replay a no-op.
CREATE TABLE user_action_token (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    token_type  VARCHAR(24) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT uq_user_action_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_action_token_type CHECK (token_type IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
--rollback DROP TABLE user_action_token;
