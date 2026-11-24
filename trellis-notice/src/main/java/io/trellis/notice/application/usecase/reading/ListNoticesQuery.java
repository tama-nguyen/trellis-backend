package io.trellis.notice.application.usecase.reading;

import io.trellis.notice.api.dto.NoticeAudience;

/**
 * @param first page size, clamped to {@link #MAX_FIRST}
 * @param after cursor of the previous page, or null for the first page
 * @param audience only this audience, or null for every audience the caller may see
 * @param titleContains case-insensitive title search, or null
 */
public record ListNoticesQuery(
    int first, String after, NoticeAudience audience, String titleContains) {
  public static final int DEFAULT_FIRST = 20;
  public static final int MAX_FIRST = 100;

  public ListNoticesQuery {
    first = first <= 0 ? DEFAULT_FIRST : Math.min(first, MAX_FIRST);
    titleContains = titleContains == null || titleContains.isBlank() ? null : titleContains.trim();
    after = after == null || after.isBlank() ? null : after;
  }
}
