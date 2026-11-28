package io.trellis.platform.persistence.jdbc;

import io.trellis.common.error.ConcurrentModificationException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * Conventions for native SQL with {@code JdbcClient}:
 *
 * <ul>
 *   <li>SQL lives in {@code private static final String} text blocks next to the repository method.
 *   <li>Parameters are always named ({@code :id}); string concatenation of values is forbidden.
 *   <li>JSONB is written with {@code CAST(:payload AS jsonb)} so no driver-specific type leaks into
 *       code.
 *   <li>Timestamps are {@code TIMESTAMPTZ} and cross the boundary as {@link Instant}.
 * </ul>
 */
public final class JdbcSupport {
  private static final Pattern IDENTIFIER =
      Pattern.compile("^[a-z_][a-z0-9_]{0,62}(\\.[a-z_][a-z0-9_]{0,62})?$");

  private JdbcSupport() {}

  public static Timestamp timestamp(final Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  public static Instant instant(final ResultSet rs, final String column) throws SQLException {
    final OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  /**
   * Pattern for {@code ILIKE :name ESCAPE '\\'} that finds {@code text} anywhere. User text inside
   * LIKE must not act as a wildcard.
   */
  public static String containsPattern(final String text) {
    final String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  /**
   * Optimistic-lock check: an {@code UPDATE ... WHERE id = :id AND version = :version} that touched
   * no row means someone else won the race.
   */
  public static void requireUpdated(final int rows, final String aggregateType, final Object id) {
    if (rows == 0) {
      throw new ConcurrentModificationException(aggregateType, String.valueOf(id));
    }
  }

  /**
   * Guards identifiers that must be spliced into SQL (ORDER BY columns), because they cannot be
   * bound as parameters. Only lower-case identifiers, optionally table-qualified, pass.
   */
  public static String identifier(final String name) {
    if (name == null || !IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("Not a safe SQL identifier: " + name);
    }
    return name;
  }
}
