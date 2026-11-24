--liquibase formatted sql

--changeset trellis:notice-002-role-permissions
--comment: Permissions of the sample module, attached to the system roles. "Staff" is a permission, not a role
--comment: check in code: whoever holds notice:read-staff sees STAFF notices.
INSERT INTO role_permission (role_id, permission) VALUES
    ('0193a5c0-0001-7000-8000-000000000003', 'notice:read'),
    ('0193a5c0-0001-7000-8000-000000000004', 'notice:read'),
    ('0193a5c0-0001-7000-8000-000000000004', 'notice:read-staff'),
    ('0193a5c0-0001-7000-8000-000000000005', 'notice:read'),
    ('0193a5c0-0001-7000-8000-000000000005', 'notice:read-staff'),
    ('0193a5c0-0001-7000-8000-000000000005', 'notice:manage')
ON CONFLICT (role_id, permission) DO NOTHING;
--rollback DELETE FROM role_permission WHERE permission LIKE 'notice:%';
