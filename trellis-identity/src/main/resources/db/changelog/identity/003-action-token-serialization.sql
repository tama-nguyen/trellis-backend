--liquibase formatted sql

--changeset trellis:identity-005-outstanding-action
--comment: Retain the latest outstanding action before enforcing the issuance invariant.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, token_type ORDER BY created_at DESC, id DESC) AS position
    FROM user_action_token WHERE consumed_at IS NULL
)
UPDATE user_action_token SET consumed_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT id FROM ranked WHERE position > 1);
CREATE UNIQUE INDEX uq_user_action_outstanding ON user_action_token (user_id, token_type)
WHERE consumed_at IS NULL;
--rollback DROP INDEX uq_user_action_outstanding;
