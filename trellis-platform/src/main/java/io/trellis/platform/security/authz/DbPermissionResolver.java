package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

/** Resolves token roles and stored grants through the same role-to-permission table. */
public final class DbPermissionResolver implements PermissionResolver {
    private final RoleGrantStore grants;
    private final GrantCache cache;
    private final Clock clock;

    public DbPermissionResolver(RoleGrantStore grants, GrantCache cache, Clock clock) {
        this.grants = grants;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public Set<Permission> permissionsOf(Actor actor, ResourceScope scope) {
        Set<Permission> permissions = new HashSet<>(actor.permissions());
        permissions.addAll(cache.byRoles(actor.roles(), () -> grants.permissionsForRoles(actor.roles())));
        GrantSubject.of(actor, clock.instant())
                .ifPresent(subject -> permissions.addAll(
                        cache.bySubject(subject, scope, () -> grants.permissionsForSubject(subject, scope))));
        return Set.copyOf(permissions);
    }
}
