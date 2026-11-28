--liquibase formatted sql

--changeset tam.nguyenthanh:learning-content-002-role-permissions
--comment: Permissions of the course module. 'learner' is the role every native account gets from
--comment: trellis.identity.default-role, so it must stay the least-privileged entry here.
INSERT INTO role_permission (role_id, permission) VALUES
    ('0193a5c0-0001-7000-8000-000000000002', 'course:read'),
    ('0193a5c0-0001-7000-8000-000000000001', 'course:read'),
    ('0193a5c0-0001-7000-8000-000000000001', 'course:manage')
ON CONFLICT (role_id, permission) DO NOTHING;
--rollback DELETE FROM role_permission WHERE permission LIKE 'course:%';
