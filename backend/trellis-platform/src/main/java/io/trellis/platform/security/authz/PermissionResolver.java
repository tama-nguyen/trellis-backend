package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import java.util.Set;

/**
 * SPI: which permissions an actor holds. The default maps token roles through {@code
 * trellis.security.role-permissions} and accepts permission-shaped authorities as-is. A project replaces it
 * (one bean) to read grants from its own tables or a policy engine.
 */
@FunctionalInterface
public interface PermissionResolver {
    Set<Permission> permissionsOf(Actor actor);
}
