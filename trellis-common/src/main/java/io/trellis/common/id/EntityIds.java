package io.trellis.common.id;

/** Canonical positive decimal identifiers at native entity boundaries. */
public final class EntityIds {
  private EntityIds() {}

  public static Long parse(final String value) {
    if (value == null || value.isEmpty() || value.charAt(0) < '1' || value.charAt(0) > '9') {
      throw new IllegalArgumentException("Invalid entity ID");
    }
    for (int index = 1; index < value.length(); index++) {
      if (value.charAt(index) < '0' || value.charAt(index) > '9') {
        throw new IllegalArgumentException("Invalid entity ID");
      }
    }
    return Long.valueOf(value);
  }
}
