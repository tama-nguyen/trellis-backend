package io.trellis.platform.security.authz;

import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRegistry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns permission strings from outside the code into {@link Permission} values, keeping only the ones a
 * {@code @PermissionCatalog} declares.
 *
 * <p>Both sources are untrusted in the same way: a token claim is written by the identity provider, and a
 * {@code role_permission} row by whoever ran the last migration or SQL statement. Neither is compile-checked,
 * so both pass through here rather than through {@link Permission#of} directly - that method throws, and an
 * exception raised while deciding authorization would turn one bad row into a 500 on every request of every
 * actor holding that role.
 *
 * <p>A bad value is dropped with a warning naming its source. Dropping is safe: a permission no catalog
 * declares can never satisfy a requirement, because requirements are built from catalog constants. Warning is
 * the point - otherwise the only symptom is an unexplained 403.
 */
@Slf4j
public final class DeclaredPermissions {
    /** A claim in a bearer token. */
    public static final String SOURCE_TOKEN = "token";
    /** A row of the {@code role_permission} table. */
    public static final String SOURCE_ROLE_MAPPING = "role_permission";

    private DeclaredPermissions() {}

    public static Set<Permission> parse(Collection<String> values, PermissionRegistry registry, String source) {
        Set<Permission> permissions = new HashSet<>();
        for (String value : values) {
            parse(value, registry, source).ifPresent(permissions::add);
        }
        return Set.copyOf(permissions);
    }

    private static Optional<Permission> parse(String value, PermissionRegistry registry, String source) {
        Permission permission;
        try {
            permission = Permission.of(value);
        } catch (IllegalArgumentException malformed) {
            log.atWarn()
                    .addKeyValue("permission", value)
                    .addKeyValue("source", source)
                    .log("Ignoring malformed permission");
            return Optional.empty();
        }
        if (!registry.isDeclared(permission)) {
            log.atWarn()
                    .addKeyValue("permission", value)
                    .addKeyValue("source", source)
                    .log("Ignoring undeclared permission");
            return Optional.empty();
        }
        return Optional.of(permission);
    }
}
