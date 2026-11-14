package io.trellis.common.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What an operation needs: every permission in {@code allOf} and at least one in {@code anyOf}. An empty
 * requirement is a configuration error, never "allow".
 */
public record PermissionRequirement(Set<Permission> anyOf, Set<Permission> allOf) {
    public PermissionRequirement {
        anyOf = Set.copyOf(anyOf);
        allOf = Set.copyOf(allOf);
        if (anyOf.isEmpty() && allOf.isEmpty()) {
            throw new IllegalArgumentException("A permission requirement must name at least one permission");
        }
    }

    public static PermissionRequirement of(String[] anyOf, String[] allOf) {
        return new PermissionRequirement(toSet(anyOf), toSet(allOf));
    }

    public static PermissionRequirement anyOf(String... permissions) {
        return new PermissionRequirement(toSet(permissions), Set.of());
    }

    public static PermissionRequirement allOf(String... permissions) {
        return new PermissionRequirement(Set.of(), toSet(permissions));
    }

    /** Whether {@code granted} satisfies this requirement. */
    public boolean isSatisfiedBy(Set<Permission> granted) {
        boolean all = granted.containsAll(allOf);
        boolean any = anyOf.isEmpty() || anyOf.stream().anyMatch(granted::contains);
        return all && any;
    }

    private static Set<Permission> toSet(String[] values) {
        return Arrays.stream(values).map(Permission::of).collect(Collectors.toUnmodifiableSet());
    }
}
