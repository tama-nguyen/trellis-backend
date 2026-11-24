package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import io.trellis.common.security.ResourceScope;
import java.util.Set;

/**
 * SPI: which declared permissions an actor holds at one explicit resource scope.
 */
@FunctionalInterface
public interface PermissionResolver {
    Set<Permission> permissionsOf(Actor actor, ResourceScope scope);
}
