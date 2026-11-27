-- Requires prepare-principal-mapping.sql and an explicitly reviewed manifest in this transaction.
-- No COMMIT: the operator first runs a dry run ending in ROLLBACK.
-- Pending events, idempotency scopes and other product-specific references need separate review.
LOCK TABLE role_grant, course, notification IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMPORARY TABLE principal_change_map ON COMMIT DROP AS
SELECT mapping.*,
    CASE WHEN direction.reverse THEN mapping.encoded_id ELSE mapping.legacy_id END AS source_id,
    CASE WHEN direction.reverse THEN mapping.legacy_id ELSE mapping.encoded_id END AS target_id
FROM principal_identity_map mapping CROSS JOIN principal_migration_direction direction;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM principal_identity_map)
        OR EXISTS (SELECT 1 FROM principal_identity_map WHERE NOT provenance_reviewed) THEN
        RAISE EXCEPTION 'Principal mappings require explicit provenance review' USING ERRCODE = '22023';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM principal_grant_map)
        AND NOT EXISTS (SELECT 1 FROM principal_course_map)
        AND NOT EXISTS (SELECT 1 FROM principal_notification_map) THEN
        RAISE EXCEPTION 'No rows selected for principal migration' USING ERRCODE = '22023';
    END IF;

    IF EXISTS (
        SELECT 1 FROM principal_grant_map selected
        JOIN principal_change_map mapping USING (mapping_key)
        LEFT JOIN role_grant grant_row ON grant_row.id = selected.grant_id
        WHERE grant_row.id IS NULL OR grant_row.subject_type <> mapping.subject_type
            OR (grant_row.subject_id IS DISTINCT FROM mapping.source_id
                AND grant_row.subject_id IS DISTINCT FROM mapping.target_id)
    ) THEN
        RAISE EXCEPTION 'Selected grant no longer matches the reviewed principal' USING ERRCODE = '22023';
    END IF;

    IF EXISTS (
        SELECT 1 FROM principal_course_map selected
        JOIN principal_change_map mapping USING (mapping_key)
        LEFT JOIN course resource ON resource.id = selected.course_id
        WHERE resource.id IS NULL
            OR (resource.owner_id IS DISTINCT FROM mapping.source_id
                AND resource.owner_id IS DISTINCT FROM mapping.target_id)
    ) THEN
        RAISE EXCEPTION 'Selected course no longer matches the reviewed owner' USING ERRCODE = '22023';
    END IF;

    IF EXISTS (
        SELECT 1 FROM principal_notification_map selected
        LEFT JOIN principal_change_map author ON author.mapping_key = selected.author_mapping_key
        LEFT JOIN principal_change_map publisher ON publisher.mapping_key = selected.publisher_mapping_key
        LEFT JOIN notification resource ON resource.id = selected.notification_id
        WHERE resource.id IS NULL
            OR (author.mapping_key IS NOT NULL
                AND resource.author_id IS DISTINCT FROM author.source_id
                AND resource.author_id IS DISTINCT FROM author.target_id)
            OR (publisher.mapping_key IS NOT NULL
                AND resource.published_by IS DISTINCT FROM publisher.source_id
                AND resource.published_by IS DISTINCT FROM publisher.target_id)
    ) THEN
        RAISE EXCEPTION 'Selected notification no longer matches the reviewed principal' USING ERRCODE = '22023';
    END IF;

    -- Check the complete resulting key set before changing any ownership or grant row.
    IF EXISTS (
        SELECT 1 FROM role_grant grant_row
        LEFT JOIN principal_grant_map selected ON selected.grant_id = grant_row.id
        LEFT JOIN principal_change_map mapping USING (mapping_key)
        GROUP BY grant_row.subject_type, COALESCE(mapping.target_id, grant_row.subject_id),
            grant_row.role_id, grant_row.scope_type, grant_row.scope_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Principal migration would merge conflicting grants' USING ERRCODE = '23505';
    END IF;
END $$;

UPDATE role_grant grant_row SET subject_id = mapping.target_id
FROM principal_grant_map selected
JOIN principal_change_map mapping USING (mapping_key)
WHERE grant_row.id = selected.grant_id AND grant_row.subject_id <> mapping.target_id;

UPDATE course resource SET owner_id = mapping.target_id
FROM principal_course_map selected
JOIN principal_change_map mapping USING (mapping_key)
WHERE resource.id = selected.course_id AND resource.owner_id <> mapping.target_id;

UPDATE notification resource
SET author_id = COALESCE(author.target_id, resource.author_id),
    published_by = COALESCE(publisher.target_id, resource.published_by)
FROM principal_notification_map selected
LEFT JOIN principal_change_map author ON author.mapping_key = selected.author_mapping_key
LEFT JOIN principal_change_map publisher ON publisher.mapping_key = selected.publisher_mapping_key
WHERE resource.id = selected.notification_id
    AND (resource.author_id IS DISTINCT FROM COALESCE(author.target_id, resource.author_id)
        OR resource.published_by IS DISTINCT FROM COALESCE(publisher.target_id, resource.published_by));
