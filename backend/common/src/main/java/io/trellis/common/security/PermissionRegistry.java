package io.trellis.common.security;

import java.util.Set;

/**
 * The set of permissions declared by {@link PermissionCatalog} classes. Used to fail fast at startup when code
 * or configuration names a permission nobody declared.
 */
public interface PermissionRegistry {
    Set<Permission> all();

    default boolean isDeclared(Permission permission) {
        return all().contains(permission);
    }
}
