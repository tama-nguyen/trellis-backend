package io.trellis.common.validation;

import io.trellis.common.error.ValidationException;
import java.util.ArrayList;
import java.util.List;

/** Collector of {@link Violation}s. Not thread-safe; one per validation run. */
public final class Violations {
  private final List<Violation> items = new ArrayList<>();

  public Violations add(String field, String code, String message) {
    items.add(new Violation(field, code, message));
    return this;
  }

  /** Adds a violation unless {@code condition} holds. */
  public Violations check(boolean condition, String field, String code, String message) {
    if (!condition) {
      add(field, code, message);
    }
    return this;
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public List<Violation> asList() {
    return List.copyOf(items);
  }

  public void throwIfAny() {
    if (!items.isEmpty()) {
      throw new ValidationException(items);
    }
  }
}
