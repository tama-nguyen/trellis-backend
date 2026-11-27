package io.trellis.common.security;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named capability, {@code <resource>:<action>} in kebab case, e.g. {@code notification:publish}.
 *
 * <p>Declare permissions as {@code public static final String} constants in a class annotated
 * {@link PermissionCatalog} (annotation attributes need compile-time strings), and turn them into
 * values with {@link #of(String)}.
 */
public record Permission(String value) {
  private static final Pattern FORMAT =
      Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*:[a-z][a-z0-9-]*$");

  public Permission {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Permission must look like 'resource:action': " + value);
    }
  }

  public static Permission of(String value) {
    return new Permission(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
