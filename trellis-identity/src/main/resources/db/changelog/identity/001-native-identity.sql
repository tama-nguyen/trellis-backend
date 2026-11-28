--liquibase formatted sql

--changeset trellis:identity-001-users
--comment: Native user aggregate. email is the public login id; status gates sign-in. Plural, because "user"
--comment: is a reserved word in PostgreSQL and would need quoting in every statement.
CREATE SEQUENCE users_id_seq INCREMENT BY 1;
CREATE TABLE users (
    id                BIGINT DEFAULT nextval('users_id_seq')         PRIMARY KEY,
    email             VARCHAR(320) NOT NULL,
    display_name      VARCHAR(200) NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'))
);
--rollback DROP TABLE users; DROP SEQUENCE users_id_seq;

--changeset trellis:identity-002-user-password
--comment: Password hash kept out of the user row so reading a user never carries the credential.
CREATE TABLE user_password (
    user_id             BIGINT         PRIMARY KEY,
    password_hash       VARCHAR(100) NOT NULL,
    password_changed_at TIMESTAMPTZ  NOT NULL
);
--rollback DROP TABLE user_password;

--changeset trellis:identity-003-user-session
--comment: One sign-in. Only token hashes are stored; the raw tokens exist solely in the client.
--comment: VARCHAR, not CHAR: bpchar blank-pads and compares with trailing spaces ignored, which is the wrong
--comment: semantics for a digest, and Hibernate cannot validate it without a vendor annotation.
CREATE SEQUENCE user_session_id_seq INCREMENT BY 1;
CREATE TABLE user_session (
    id                 BIGINT DEFAULT nextval('user_session_id_seq')        PRIMARY KEY,
    user_id            BIGINT        NOT NULL,
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
--rollback DROP TABLE user_session; DROP SEQUENCE user_session_id_seq;

--changeset trellis:identity-004-user-action-token
--comment: Single-use email verification and password reset tokens; consumed_at makes replay a no-op.
CREATE SEQUENCE user_action_token_id_seq INCREMENT BY 1;
CREATE TABLE user_action_token (
    id          BIGINT DEFAULT nextval('user_action_token_id_seq')        PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_type  VARCHAR(24) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT uq_user_action_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_action_token_type CHECK (token_type IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
--rollback DROP TABLE user_action_token; DROP SEQUENCE user_action_token_id_seq;
