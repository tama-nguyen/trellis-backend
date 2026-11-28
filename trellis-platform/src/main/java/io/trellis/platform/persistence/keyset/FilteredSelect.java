package io.trellis.platform.persistence.keyset;

import io.trellis.common.page.Cursor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The statement behind one keyset page: a base SELECT ending in {@code WHERE TRUE}, the conditions
 * that apply to this request, then the seek. Conditions are written without the leading AND, and a
 * value reaches SQL only as a named parameter.
 */
public final class FilteredSelect {
  private final StringBuilder sql;
  private final Map<String, Object> parameters = new HashMap<>();

  private FilteredSelect(final String select) {
    this.sql = new StringBuilder(select);
  }

  public static FilteredSelect from(final String select) {
    return new FilteredSelect(select);
  }

  public FilteredSelect and(final String condition) {
    sql.append(" AND ").append(condition);
    return this;
  }

  /** The value may be null: SQL then compares with NULL, which matches no row. */
  public FilteredSelect bind(final String name, final Object value) {
    parameters.put(name, value);
    return this;
  }

  /** Applies the condition only when the client sent a non-blank text. */
  public FilteredSelect andIfPresent(
      final String condition, final String name, final String value) {
    if (value == null || value.isBlank()) {
      return this;
    }
    return and(condition).bind(name, value);
  }

  /** Applies the condition only when the value is not null, bound as {@code toParameter} of it. */
  public <T> FilteredSelect andIfPresent(
      final String condition,
      final String name,
      final T value,
      final Function<? super T, ?> toParameter) {
    if (value == null) {
      return this;
    }
    return and(condition).bind(name, toParameter.apply(value));
  }

  /** Appends the seek predicate, the order and the limit: call it after the last condition. */
  public FilteredSelect seek(final KeysetQuery keyset, final Cursor cursor, final int limit) {
    parameters.putAll(keyset.parameters(cursor, limit));
    sql.append(keyset.where(cursor, "AND")).append(keyset.orderBy()).append(keyset.limit());
    return this;
  }

  public String sql() {
    return sql.toString();
  }

  public Map<String, Object> parameters() {
    return Collections.unmodifiableMap(parameters);
  }
}
