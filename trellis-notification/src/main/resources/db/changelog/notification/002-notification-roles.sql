--liquibase formatted sql

--changeset tam.nguyenthanh:notification-002-role-permissions
--comment: Permissions of the sample module, attached to the system roles. "Staff" is a permission, not a role
--comment: check in code: whoever holds notification:read-staff sees STAFF notifications.
INSERT INTO role_permission (role_id, permission) VALUES
    ('0193a5c0-0001-7000-8000-000000000003', 'notification:read'),
    ('0193a5c0-0001-7000-8000-000000000004', 'notification:read'),
    ('0193a5c0-0001-7000-8000-000000000004', 'notification:read-staff'),
    ('0193a5c0-0001-7000-8000-000000000005', 'notification:read'),
    ('0193a5c0-0001-7000-8000-000000000005', 'notification:read-staff'),
    ('0193a5c0-0001-7000-8000-000000000005', 'notification:manage')
ON CONFLICT (role_id, permission) DO NOTHING;
--rollback DELETE FROM role_permission WHERE permission LIKE 'notification:%';
