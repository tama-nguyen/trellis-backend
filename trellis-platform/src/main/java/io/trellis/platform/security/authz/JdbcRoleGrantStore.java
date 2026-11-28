package io.trellis.platform.security.authz;

import io.trellis.common.error.NotFoundException;
import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRegistry;
import io.trellis.common.security.ResourceScope;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.FilteredSelect;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** PostgreSQL implementation of the role mapping and scoped-grant store. */
public final class JdbcRoleGrantStore implements RoleGrantStore {
  private static final String PERMISSIONS_FOR_ROLES =
      """
            SELECT DISTINCT rp.permission
              FROM role r
              JOIN role_permission rp ON rp.role_id = r.id
             WHERE r.name IN (:roles) AND r.tenant_id IS NULL
            """;
  private static final String ROLE_ID_BY_NAME =
      """
            SELECT r.id FROM role r WHERE r.name = :name AND r.tenant_id IS NULL
            """;
  private static final String PERMISSIONS_FOR_SUBJECT =
      """
            SELECT DISTINCT rp.permission
              FROM role_grant rg
              JOIN role r ON r.id = rg.role_id AND r.tenant_id IS NULL
              JOIN role_permission rp ON rp.role_id = rg.role_id
             WHERE rg.subject_type = :subjectType
               AND rg.subject_id = :subjectId
               AND (rg.expires_at IS NULL OR rg.expires_at > :now)
               AND ((rg.scope_type = :global AND rg.scope_id = :global)
                    OR (rg.scope_type = :scopeType AND rg.scope_id = :scopeId))
            """;
  private static final String UPSERT_GRANT =
      """
            INSERT INTO role_grant
                   (subject_type, subject_id, role_id, scope_type, scope_id, granted_at, granted_by, expires_at)
            VALUES (:subjectType, :subjectId, :roleId, :scopeType, :scopeId, :grantedAt, :grantedBy, :expiresAt)
            ON CONFLICT (subject_type, subject_id, role_id, scope_type, scope_id) DO UPDATE
               SET granted_at = EXCLUDED.granted_at,
                   granted_by = EXCLUDED.granted_by,
                   expires_at = EXCLUDED.expires_at
            RETURNING id
            """;
  private static final String DELETE_GRANT = "DELETE FROM role_grant WHERE id = :id";
  private static final String PAGE_SELECT =
      """
            SELECT rg.id, rg.subject_type, rg.subject_id, r.name AS role, rg.scope_type, rg.scope_id,
                   rg.granted_at, rg.granted_by, rg.expires_at
              FROM role_grant rg
              JOIN role r ON r.id = rg.role_id
             WHERE TRUE
            """;
  private static final String SUBJECT_TYPE_IS = "rg.subject_type = :subjectType";
  private static final String SUBJECT_ID_IS = "rg.subject_id = :subjectId";
  private static final String ROLE_IS = "r.name = :role";
  private static final String SCOPE_TYPE_IS = "rg.scope_type = :scopeType";
  private static final String SCOPE_ID_IS = "rg.scope_id = :scopeId";
  private static final KeysetQuery KEYSET =
      KeysetQuery.descending(SortKey.of("rg.id", KeyType.LONG));

  private static final RowMapper<RoleGrant> MAPPER =
      (rs, row) ->
          new RoleGrant(
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
  private final PermissionRegistry permissionRegistry;

  public JdbcRoleGrantStore(
      final JdbcClient jdbc,
      final CursorCodec cursors,
      final PermissionRegistry permissionRegistry) {
    this.jdbc = jdbc;
    this.cursors = cursors;
    this.permissionRegistry = permissionRegistry;
  }

  @Override
  public Set<Permission> permissionsForRoles(final Set<String> roles) {
    if (roles.isEmpty()) {
      return Set.of();
    }
    return permissions(
        jdbc.sql(PERMISSIONS_FOR_ROLES).param("roles", roles).query(String.class).list());
  }

  @Override
  public Set<Permission> permissionsForSubject(
      final GrantSubject subject, final ResourceScope scope) {
    return permissions(
        jdbc.sql(PERMISSIONS_FOR_SUBJECT)
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
      final GrantSubjectType subjectType,
      final String subjectId,
      final String role,
      final ResourceScope scope,
      final Instant grantedAt,
      final String grantedBy,
      final Instant expiresAt) {
    final UUID roleId = roleIdByName(role);
    final long id =
        jdbc.sql(UPSERT_GRANT)
            .param("subjectType", subjectType.name())
            .param("subjectId", subjectId)
            .param("roleId", roleId)
            .param("scopeType", scope.type())
            .param("scopeId", scope.id())
            .param("grantedAt", Timestamp.from(grantedAt))
            .param("grantedBy", grantedBy)
            .param(
                "expiresAt",
                expiresAt == null ? null : expiresAt.atOffset(ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .query(Long.class)
            .single();
    return new RoleGrant(id, subjectType, subjectId, role, scope, grantedAt, grantedBy, expiresAt);
  }

  /**
   * A grant names a role the way a token does - by name - so an unknown name is a caller mistake,
   * not a row to create silently. Roles are defined by migrations, never by granting one.
   */
  private UUID roleIdByName(final String role) {
    return jdbc.sql(ROLE_ID_BY_NAME)
        .param("name", role)
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> new NotFoundException("Role " + role + " is not defined"));
  }

  @Override
  public boolean revoke(final long id) {
    return jdbc.sql(DELETE_GRANT).param("id", id).update() == 1;
  }

  @Override
  public CursorPage<RoleGrant> page(
      final RoleGrantFilter filter, final String after, final int limit) {
    final Cursor cursor = after == null ? null : cursors.decode(after);
    final FilteredSelect select = select(filter).seek(KEYSET, cursor, limit);
    final List<RoleGrant> rows =
        jdbc.sql(select.sql()).params(select.parameters()).query(MAPPER).list();

    return KEYSET.page(rows, limit, grant -> List.of(grant.id()), cursors);
  }

  private static FilteredSelect select(final RoleGrantFilter filter) {
    return FilteredSelect.from(PAGE_SELECT)
        .andIfPresent(SUBJECT_TYPE_IS, "subjectType", filter.subjectType(), GrantSubjectType::name)
        .andIfPresent(SUBJECT_ID_IS, "subjectId", filter.subjectId())
        .andIfPresent(ROLE_IS, "role", filter.role())
        .andIfPresent(SCOPE_TYPE_IS, "scopeType", filter.scopeType())
        .andIfPresent(SCOPE_ID_IS, "scopeId", filter.scopeId());
  }

  private Set<Permission> permissions(final List<String> values) {
    return DeclaredPermissions.parse(
        values, permissionRegistry, DeclaredPermissions.SOURCE_ROLE_MAPPING);
  }
}
