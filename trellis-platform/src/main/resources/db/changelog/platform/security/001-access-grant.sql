--liquibase formatted sql

--changeset tam.nguyenthanh:security-001-role
--comment: A role is a row, not a string: it has an owner (tenant_id NULL means a system role) and an identity
--comment: of its own, so two tenants can both define "Manager" with different permissions. Names are matched
--comment: against the identity provider's role claim, which is why they stay unique per tenant.
CREATE TABLE role (
    id          UUID         PRIMARY KEY,
    tenant_id   VARCHAR(100),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_role_name UNIQUE NULLS NOT DISTINCT (tenant_id, name)
);
--rollback DROP TABLE role;

--changeset tam.nguyenthanh:security-002-role-permission
--comment: What a role may do. One source of truth for roles that arrive in a token and roles granted here.
CREATE TABLE role_permission (
    role_id    UUID         NOT NULL,
    permission VARCHAR(150) NOT NULL,
    PRIMARY KEY (role_id, permission)
);
--rollback DROP TABLE role_permission;

--changeset tam.nguyenthanh:security-003-role-grant
--comment: Who holds which role, over which scope. scope_type '*' is a global grant. No secondary index:
--comment: the unique constraint leads with subject_type and subject_id, which is how lookups arrive.
CREATE TABLE role_grant (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_type VARCHAR(16)  NOT NULL,
    subject_id   VARCHAR(200) NOT NULL,
    role_id      UUID         NOT NULL,
    scope_type   VARCHAR(60)  NOT NULL,
    scope_id     VARCHAR(200) NOT NULL,
    granted_at   TIMESTAMPTZ  NOT NULL,
    granted_by   VARCHAR(200) NOT NULL,
    expires_at   TIMESTAMPTZ,
    CONSTRAINT uq_role_grant UNIQUE (subject_type, subject_id, role_id, scope_type, scope_id),
    CONSTRAINT ck_role_grant_subject_type CHECK (subject_type IN ('USER', 'SERVICE'))
);
--rollback DROP TABLE role_grant;
