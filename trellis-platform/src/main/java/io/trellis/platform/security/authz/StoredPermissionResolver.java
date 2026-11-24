package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Answers "which permissions does this actor hold, here?" from what this database stores.
 *
 * <p>Three sources, united: permissions the token carries outright, permissions the token's roles
 * map to, and permissions from grants stored for this subject - either global or naming this exact
 * resource. Both role paths go through {@code role_permission}, so "what can this role do" has one
 * answer whether the role arrived in a token or was granted here.
 *
 * <p>It is separate from {@link DefaultAuthorizationService} because the two answer different
 * questions, and only one of them is allowed to vary. The service holds the rule - deny by default,
 * system actor allowed, anonymous refused - and is final so no implementation can soften it. This
 * class only says where permissions come from, and is the piece a product swaps (one {@link
 * PermissionResolver} bean) to read its own tables or to call a policy engine.
 */
@RequiredArgsConstructor
public final class StoredPermissionResolver implements PermissionResolver {

  private final RoleGrantStore grants;
  private final GrantCache cache;
  private final Clock clock;

  @Override
  public Set<Permission> permissionsOf(Actor actor, ResourceScope scope) {
    Set<Permission> permissions = new HashSet<>(actor.permissions());
    permissions.addAll(
        cache.byRoles(actor.roles(), () -> grants.permissionsForRoles(actor.roles())));
    // The system actor and an anonymous caller hold no grant, so neither reaches the store.
    GrantSubject.of(actor, clock.instant())
        .ifPresent(
            subject ->
                permissions.addAll(
                    cache.bySubject(
                        subject, scope, () -> grants.permissionsForSubject(subject, scope))));
    return Set.copyOf(permissions);
  }
}
