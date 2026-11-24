package io.trellis.common.security;

import java.util.Objects;

/** The resource boundary of an authorization decision. The platform treats both values as opaque. */
public record ResourceScope(String type, String id) {
    public static final String GLOBAL = "*";

    public ResourceScope {
        type = requireValue(type, "scope type");
        id = requireValue(id, "scope id");
    }

    public static ResourceScope global() {
        return new ResourceScope(GLOBAL, GLOBAL);
    }

    public static ResourceScope of(String type, String id) {
        return new ResourceScope(type, id);
    }

    private static String requireValue(String value, String label) {
        if (Objects.requireNonNull(value, label).isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
