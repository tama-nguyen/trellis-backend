package io.trellis.platform.security.authz;

import io.trellis.common.page.CursorPage;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import java.time.Instant;
import java.util.Set;

/** Persistence port for role mappings and scoped grants. */
public interface RoleGrantStore {
    Set<Permission> permissionsForRoles(Set<String> roles);

    Set<Permission> permissionsForSubject(GrantSubject subject, ResourceScope scope);

    RoleGrant grant(
            GrantSubjectType subjectType,
            String subjectId,
            String role,
            ResourceScope scope,
            Instant grantedAt,
            String grantedBy,
            Instant expiresAt);

    boolean revoke(long id);

    CursorPage<RoleGrant> page(RoleGrantFilter filter, String after, int limit);
}
