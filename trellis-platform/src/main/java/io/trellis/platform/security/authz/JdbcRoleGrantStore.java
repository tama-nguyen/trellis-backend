package io.trellis.platform.security.authz;

import io.trellis.common.error.NotFoundException;
import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRegistry;
import io.trellis.common.security.ResourceScope;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PostgreSQL implementation of the role mapping and scoped-grant store. */
public final class JdbcRoleGrantStore implements RoleGrantStore {
    private static final String PERMISSIONS_FOR_ROLES = """
            SELECT DISTINCT rp.permission
              FROM role r
              JOIN role_permission rp ON rp.role_id = r.id
             WHERE r.name IN (:roles)
            """;
    private static final String ROLE_ID_BY_NAME = """
            SELECT r.id FROM role r WHERE r.name = :name AND r.tenant_id IS NULL
            """;
    private static final String PERMISSIONS_FOR_SUBJECT = """
            SELECT DISTINCT rp.permission
              FROM role_grant rg
              JOIN role_permission rp ON rp.role_id = rg.role_id
             WHERE rg.subject_type = :subjectType
               AND rg.subject_id = :subjectId
               AND (rg.expires_at IS NULL OR rg.expires_at > :now)
               AND ((rg.scope_type = :global AND rg.scope_id = :global)
                    OR (rg.scope_type = :scopeType AND rg.scope_id = :scopeId))
            """;
    private static final String UPSERT_GRANT = """
            INSERT INTO role_grant
                   (subject_type, subject_id, role_id, scope_type, scope_id, granted_at, granted_by, expires_at)
            VALUES (:subjectType, :subjectId, :roleId, :scopeType, :scopeId, :grantedAt, :grantedBy, :expiresAt)
            ON CONFLICT (subject_type, subject_id, role_id, scope_type, scope_id) DO UPDATE
               SET granted_at = EXCLUDED.granted_at,
                   granted_by = EXCLUDED.granted_by,
                   expires_at = EXCLUDED.expires_at
            RETURNING id, granted_at
            """;
    private static final String DELETE_GRANT = "DELETE FROM role_grant WHERE id = :id";
    private static final String PAGE_SELECT = """
            SELECT rg.id, rg.subject_type, rg.subject_id, r.name AS role, rg.scope_type, rg.scope_id,
                   rg.granted_at, rg.granted_by, rg.expires_at
              FROM role_grant rg
              JOIN role r ON r.id = rg.role_id
             WHERE TRUE
            """;
    private static final String SUBJECT_TYPE_IS = " AND rg.subject_type = :subjectType ";
    private static final String SUBJECT_ID_IS = " AND rg.subject_id = :subjectId ";
    private static final String ROLE_IS = " AND r.name = :role ";
    private static final String SCOPE_TYPE_IS = " AND rg.scope_type = :scopeType ";
    private static final String SCOPE_ID_IS = " AND rg.scope_id = :scopeId ";
    private static final KeysetQuery KEYSET = KeysetQuery.descending(SortKey.of("rg.id", KeyType.LONG));

    private static final RowMapper<RoleGrant> MAPPER = (rs, row) -> new RoleGrant(
            rs.getLong("id"),
            GrantSubjectType.valueOf(rs.getString("subject_type")),
            rs.getString("subject_id"),
            rs.getString("role"),
            ResourceScope.of(rs.getString("scope_type"), rs.getString("scope_id")),
            JdbcSupport.instant(rs, "granted_at"),
            rs.getString("granted_by"),
            JdbcSupport.instant(rs, "expires_at"));

    private final JdbcClient jdbc;
    private final CursorCodec cursors;
    private final GrantCache cache;
    private final PermissionRegistry permissionRegistry;

    public JdbcRoleGrantStore(
            JdbcClient jdbc, CursorCodec cursors, GrantCache cache, PermissionRegistry permissionRegistry) {
        this.jdbc = jdbc;
        this.cursors = cursors;
        this.cache = cache;
        this.permissionRegistry = permissionRegistry;
    }

    @Override
    public Set<Permission> permissionsForRoles(Set<String> roles) {
        if (roles.isEmpty()) {
            return Set.of();
        }
        return permissions(jdbc.sql(PERMISSIONS_FOR_ROLES)
                .param("roles", roles)
                .query(String.class)
                .list());
    }

    @Override
    public Set<Permission> permissionsForSubject(GrantSubject subject, ResourceScope scope) {
        return permissions(jdbc.sql(PERMISSIONS_FOR_SUBJECT)
                .param("subjectType", subject.type().name())
                .param("subjectId", subject.id())
                .param("now", Timestamp.from(subject.asOf()))
                .param("global", ResourceScope.GLOBAL)
                .param("scopeType", scope.type())
                .param("scopeId", scope.id())
                .query(String.class)
                .list());
    }

    @Override
    public RoleGrant grant(
            GrantSubjectType subjectType,
            String subjectId,
            String role,
            ResourceScope scope,
            Instant grantedAt,
            String grantedBy,
            Instant expiresAt) {
        UUID roleId = roleIdByName(role);
        long id = jdbc.sql(UPSERT_GRANT)
                .param("subjectType", subjectType.name())
                .param("subjectId", subjectId)
                .param("roleId", roleId)
                .param("scopeType", scope.type())
                .param("scopeId", scope.id())
                .param("grantedAt", Timestamp.from(grantedAt))
                .param("grantedBy", grantedBy)
                .param("expiresAt", expiresAt == null ? null : Timestamp.from(expiresAt), Types.TIMESTAMP_WITH_TIMEZONE)
                .query(Long.class)
                .single();
        cache.invalidateAll();
        return new RoleGrant(id, subjectType, subjectId, role, scope, grantedAt, grantedBy, expiresAt);
    }

    /**
     * A grant names a role the way a token does - by name - so an unknown name is a caller mistake, not a row
     * to create silently. Roles are defined by migrations, never by granting one.
     */
    private UUID roleIdByName(String role) {
        return jdbc.sql(ROLE_ID_BY_NAME)
                .param("name", role)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new NotFoundException("Role " + role + " is not defined"));
    }

    @Override
    public boolean revoke(long id) {
        boolean removed = jdbc.sql(DELETE_GRANT).param("id", id).update() == 1;
        if (removed) {
            cache.invalidateAll();
        }
        return removed;
    }

    @Override
    public CursorPage<RoleGrant> page(RoleGrantFilter filter, String after, int limit) {
        Cursor cursor = after == null ? null : cursors.decode(after);
        StringBuilder sql = new StringBuilder(PAGE_SELECT);
        Map<String, Object> params = new HashMap<>(KEYSET.parameters(cursor, limit));
        if (filter.subjectType() != null) {
            sql.append(SUBJECT_TYPE_IS);
            params.put("subjectType", filter.subjectType().name());
        }
        append(sql, params, SUBJECT_ID_IS, "subjectId", filter.subjectId());
        append(sql, params, ROLE_IS, "role", filter.role());
        append(sql, params, SCOPE_TYPE_IS, "scopeType", filter.scopeType());
        append(sql, params, SCOPE_ID_IS, "scopeId", filter.scopeId());
        sql.append(KEYSET.where(cursor, "AND")).append(KEYSET.orderBy()).append(KEYSET.limit());
        List<RoleGrant> rows =
                jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
        return KEYSET.page(rows, limit, grant -> List.of(grant.id()), cursors);
    }

    private Set<Permission> permissions(List<String> values) {
        return DeclaredPermissions.parse(values, permissionRegistry, DeclaredPermissions.SOURCE_ROLE_MAPPING);
    }

    private static void append(
            StringBuilder sql, Map<String, Object> params, String fragment, String name, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(fragment);
            params.put(name, value);
        }
    }
}
