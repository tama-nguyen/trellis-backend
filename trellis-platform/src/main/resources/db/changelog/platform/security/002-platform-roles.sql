--liquibase formatted sql

--changeset trellis:security-004-system-roles
--comment: System roles (tenant_id NULL). Ids are fixed so later changesets and seed data can name them
--comment: without looking anything up. Each module adds its own permissions to these roles.
INSERT INTO role (id, tenant_id, name, description, created_at) VALUES
    ('0193a5c0-0001-7000-8000-000000000001', NULL, 'platform-admin', 'Operates the platform: replay, rebuild, audit, access', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0001-7000-8000-000000000002', NULL, 'learner', 'Reads published learning content', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0001-7000-8000-000000000003', NULL, 'notification-reader', 'Reads notifications addressed to everyone', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0001-7000-8000-000000000004', NULL, 'staff', 'Also reads notifications addressed to staff', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0001-7000-8000-000000000005', NULL, 'notification-admin', 'Manages notifications', TIMESTAMPTZ '2026-01-01 00:00:00+00')
ON CONFLICT (tenant_id, name) DO NOTHING;
--rollback DELETE FROM role WHERE tenant_id IS NULL;

--changeset trellis:security-005-platform-role-permissions
--comment: What the platform's own role may do. Each module seeds its permissions the same way, so an empty
--comment: role_permission - which answers 403 to everything - cannot happen by forgetting a deployment step.
INSERT INTO role_permission (role_id, permission) VALUES
    ('0193a5c0-0001-7000-8000-000000000001', 'platform.events:replay'),
    ('0193a5c0-0001-7000-8000-000000000001', 'platform.read-models:rebuild'),
    ('0193a5c0-0001-7000-8000-000000000001', 'platform.audit:read'),
    ('0193a5c0-0001-7000-8000-000000000001', 'platform.role-grants:manage')
ON CONFLICT (role_id, permission) DO NOTHING;
--rollback DELETE FROM role_permission WHERE permission LIKE 'platform.%';
