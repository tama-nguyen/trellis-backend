--liquibase formatted sql

--changeset trellis:identity-005-dev-users
--comment: Local-only sign-in users, mirroring the Keycloak dev personas so both providers offer the same
--comment: three. Passwords are bcrypt(cost 12) of 'reader-dev', 'staff-dev' and 'admin-dev': weak and
--comment: committed on purpose, exactly like the rest of the local stack, which binds to 127.0.0.1 only.
--comment: Ids are fixed so the grants below can name them; they are UUIDv7-shaped but hand-written.
INSERT INTO users (id, email, display_name, status, email_verified_at, created_at, updated_at) VALUES
    ('0193a5c0-0000-7000-8000-000000000001', 'reader@example.test', 'Dev Reader', 'ACTIVE',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00',
     TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0000-7000-8000-000000000002', 'staff@example.test', 'Dev Staff', 'ACTIVE',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00',
     TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0000-7000-8000-000000000003', 'admin@example.test', 'Dev Admin', 'ACTIVE',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00',
     TIMESTAMPTZ '2026-01-01 00:00:00+00');

INSERT INTO user_password (user_id, password_hash, password_changed_at) VALUES
    ('0193a5c0-0000-7000-8000-000000000001',
     '$2y$12$dpob8j/abcmzK6Tfs/SpNO/NKcroSMKzXTCfNG3m7mpMW/Gdy90pK',
     TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0000-7000-8000-000000000002',
     '$2y$12$zTA3hRUCUj3MAJsZVfE.J.QimeiTNEIMpxmm8LbXW4SjEaQe1HfXe',
     TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('0193a5c0-0000-7000-8000-000000000003',
     '$2y$12$KPE1UiPj.KqqFlSRSTuhi.ZzZtL4C39KelLlyLIaxEOwfkeB15Z3y',
     TIMESTAMPTZ '2026-01-01 00:00:00+00');
--rollback DELETE FROM user_password WHERE user_id IN ('0193a5c0-0000-7000-8000-000000000001', '0193a5c0-0000-7000-8000-000000000002', '0193a5c0-0000-7000-8000-000000000003');
--rollback DELETE FROM users WHERE id IN ('0193a5c0-0000-7000-8000-000000000001', '0193a5c0-0000-7000-8000-000000000002', '0193a5c0-0000-7000-8000-000000000003');

--changeset trellis:identity-006-dev-user-grants
--comment: What those users may do. A native token only ever carries trellis.identity.default-role, so every
--comment: other permission comes from a stored grant - which also makes this the worked example of the grant
--comment: model. Role ids are the system roles of security-004. Scope ('*','*') means "everywhere".
INSERT INTO role_grant (subject_type, subject_id, role_id, scope_type, scope_id, granted_at, granted_by) VALUES
    ('USER', '0193a5c0-0000-7000-8000-000000000001', '0193a5c0-0001-7000-8000-000000000003', '*', '*',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', 'liquibase:dev-seed'),
    ('USER', '0193a5c0-0000-7000-8000-000000000002', '0193a5c0-0001-7000-8000-000000000004', '*', '*',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', 'liquibase:dev-seed'),
    ('USER', '0193a5c0-0000-7000-8000-000000000003', '0193a5c0-0001-7000-8000-000000000005', '*', '*',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', 'liquibase:dev-seed'),
    ('USER', '0193a5c0-0000-7000-8000-000000000003', '0193a5c0-0001-7000-8000-000000000001', '*', '*',
     TIMESTAMPTZ '2026-01-01 00:00:00+00', 'liquibase:dev-seed');
--rollback DELETE FROM role_grant WHERE granted_by = 'liquibase:dev-seed';
