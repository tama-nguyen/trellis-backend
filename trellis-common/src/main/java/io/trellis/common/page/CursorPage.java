package io.trellis.common.page;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One keyset page. {@code nextCursor} is null exactly when {@code hasNext} is false.
 *
 * @param items rows of this page, at most the requested limit
 * @param nextCursor opaque token for the following page
 * @param hasNext whether another page exists
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {
  public CursorPage {
    items = List.copyOf(items);
    if (hasNext == (nextCursor == null)) {
      throw new IllegalArgumentException("nextCursor must be present exactly when hasNext");
    }
  }

  /**
   * Builds a page from a query that fetched {@code limit + 1} rows: the extra row only proves a
   * next page exists and is dropped.
   */
  public static <T> CursorPage<T> fromLookahead(
      List<T> fetched, int limit, Function<T, Cursor> cursorOf, CursorCodec codec) {
    Objects.requireNonNull(fetched, "fetched");
    if (fetched.size() <= limit) {
      return new CursorPage<>(fetched, null, false);
    }
    List<T> page = fetched.subList(0, limit);
    return new CursorPage<>(page, codec.encode(cursorOf.apply(page.getLast())), true);
  }

  public <R> CursorPage<R> map(Function<T, R> mapper) {
    return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor, hasNext);
  }
}
