package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import java.util.Set;

/**
 * Which declared permissions an actor holds at one explicit resource scope.
 *
 * <p>The seam between deciding and storing: {@code AuthorizationService} owns the rule, this owns the lookup.
 * {@link StoredPermissionResolver} is the implementation that reads this database; replacing that one bean is
 * how a product moves the lookup elsewhere, without touching a single call site.
 */
@FunctionalInterface
public interface PermissionResolver {
    Set<Permission> permissionsOf(Actor actor, ResourceScope scope);
}
