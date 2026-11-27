package io.trellis.platform.persistence.keyset;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the SQL fragments of a keyset page. All keys share one direction so the row-value
 * comparison {@code (a, b) < (:a, :b)} works and the matching composite index serves it; the last
 * key must be unique (the UUIDv7 {@code id}).
 *
 * <pre>{@code
 * KeysetQuery q = KeysetQuery.descending(SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.UUID_KEY));
 * String sql = "SELECT ... FROM notification " + q.where(cursor, "WHERE") + q.orderBy() + q.limit();
 * List<Row> rows = jdbc.sql(sql).params(q.parameters(cursor, limit)).query(MAPPER).list();
 * return q.page(rows, limit, r -> List.of(r.createdAt(), r.id()), codec);
 * }</pre>
 */
public final class KeysetQuery {
  static final String LIMIT_PARAM = "keyset_limit";
  private static final String KEY_PARAM_PREFIX = "keyset_k";

  private final List<SortKey> keys;
  private final boolean descending;

  private KeysetQuery(List<SortKey> keys, boolean descending) {
    if (keys.isEmpty()) {
      throw new IllegalArgumentException("At least one sort key is required");
    }
    this.keys = List.copyOf(keys);
    this.descending = descending;
  }

  public static KeysetQuery descending(SortKey... keys) {
    return new KeysetQuery(List.of(keys), true);
  }

  public static KeysetQuery ascending(SortKey... keys) {
    return new KeysetQuery(List.of(keys), false);
  }

  /**
   * Seek predicate, prefixed with {@code connector} ({@code WHERE} or {@code AND}); empty on the
   * first page.
   */
  public String where(Cursor cursor, String connector) {
    if (cursor == null) {
      return "";
    }
    requireArity(cursor);
    String columns = keys.stream().map(SortKey::column).collect(Collectors.joining(", "));
    List<String> params = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      params.add(":" + KEY_PARAM_PREFIX + i);
    }
    return " "
        + connector
        + " ("
        + columns
        + ") "
        + (descending ? "<" : ">")
        + " ("
        + String.join(", ", params)
        + ") ";
  }

  public String orderBy() {
    String direction = descending ? " DESC" : " ASC";
    return " ORDER BY "
        + keys.stream().map(k -> k.column() + direction).collect(Collectors.joining(", "))
        + " ";
  }

  /** Fetches one extra row so the page knows whether another follows. */
  public String limit() {
    return " LIMIT :" + LIMIT_PARAM;
  }

  public Map<String, Object> parameters(Cursor cursor, int limit) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(LIMIT_PARAM, limit + 1);
    if (cursor != null) {
      requireArity(cursor);
      for (int i = 0; i < keys.size(); i++) {
        params.put(KEY_PARAM_PREFIX + i, keys.get(i).type().toParameter(cursor.key(i)));
      }
    }
    return params;
  }

  /**
   * @param keyValues sort-key values of a row, in key order (Instant, UUID, Long or String)
   */
  public <T> CursorPage<T> page(
      List<T> fetched, int limit, Function<T, List<Object>> keyValues, CursorCodec codec) {
    return CursorPage.fromLookahead(fetched, limit, row -> toCursor(keyValues.apply(row)), codec);
  }

  private Cursor toCursor(List<Object> values) {
    if (values.size() != keys.size()) {
      throw new IllegalArgumentException("Expected " + keys.size() + " key values");
    }
    return new Cursor(
        values.stream()
            .map(v -> v instanceof Instant i ? i.toString() : String.valueOf(v))
            .toList());
  }

  private void requireArity(Cursor cursor) {
    if (cursor.keys().size() != keys.size()) {
      // A cursor from another endpoint or an older key layout: reject rather than guess.
      throw new IllegalArgumentException("Cursor does not match this query");
    }
  }

  /** One ORDER BY column. */
  public record SortKey(String column, KeyType type) {
    public SortKey {
      JdbcSupport.identifier(column);
      Objects.requireNonNull(type, "type");
    }

    public static SortKey of(String column, KeyType type) {
      return new SortKey(column, type);
    }
  }
}
