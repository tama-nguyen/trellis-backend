package io.trellis.common.page;

import java.util.List;
import java.util.Objects;

/**
 * Decoded keyset position: the sort-key values of the last row of the previous page, in ORDER BY
 * order, as strings. The last key is always the row id, so ties never repeat or skip rows.
 */
public record Cursor(List<String> keys) {
  public Cursor {
    Objects.requireNonNull(keys, "keys");
    if (keys.isEmpty()) {
      throw new IllegalArgumentException("A cursor needs at least one key");
    }
    keys = List.copyOf(keys);
  }

  public static Cursor of(String... keys) {
    return new Cursor(List.of(keys));
  }

  public String key(int index) {
    return keys.get(index);
  }
}
