package io.trellis.common.page;

import java.util.List;
import java.util.function.Function;

/**
 * Offset page for admin screens that need totals. Costs O(offset) in the database; prefer {@link CursorPage}.
 *
 * @param page zero-based page index
 */
public record OffsetPage<T>(List<T> items, int page, int size, long total) {
    public OffsetPage {
        items = List.copyOf(items);
        if (page < 0 || size < 1 || total < 0) {
            throw new IllegalArgumentException("Invalid page bounds");
        }
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < total;
    }

    public <R> OffsetPage<R> map(Function<T, R> mapper) {
        return new OffsetPage<>(items.stream().map(mapper).toList(), page, size, total);
    }
}
