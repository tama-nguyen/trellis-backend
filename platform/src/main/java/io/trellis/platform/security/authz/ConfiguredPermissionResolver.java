package io.trellis.platform.security.authz;

import io.trellis.common.context.Actor;
import io.trellis.common.security.Permission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Roles mapped by configuration, plus authorities that already look like permissions ({@code a:b}). */
public final class ConfiguredPermissionResolver implements PermissionResolver {
    private static final Pattern PERMISSION_SHAPE = Pattern.compile("^[a-z][a-z0-9.-]*:[a-z][a-z0-9-]*$");

    private final Map<String, Set<Permission>> byRole;

    public ConfiguredPermissionResolver(Map<String, List<String>> rolePermissions) {
        Map<String, Set<Permission>> mapped = new HashMap<>();
        rolePermissions.forEach((role, perms) ->
                mapped.put(role, Set.copyOf(perms.stream().map(Permission::of).toList())));
        this.byRole = Map.copyOf(mapped);
    }

    @Override
    public Set<Permission> permissionsOf(Actor actor) {
        Set<Permission> granted = new HashSet<>();
        for (String authority : actor.authorities()) {
            granted.addAll(byRole.getOrDefault(authority, Set.of()));
            if (PERMISSION_SHAPE.matcher(authority).matches()) {
                granted.add(Permission.of(authority));
            }
        }
        return granted;
    }

    Set<Permission> configuredPermissions() {
        Set<Permission> all = new HashSet<>();
        byRole.values().forEach(all::addAll);
        return all;
    }
}
