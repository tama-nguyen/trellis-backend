package io.trellis.course.application.query;

import io.trellis.course.api.dto.CourseStatus;

/**
 * Criteria of the list use case. A record, so REST query parameters and GraphQL arguments build the same object.
 *
 * @param first page size, clamped to {@link #MAX_FIRST}
 * @param after cursor of the previous page, or null for the first page
 * @param status only this status, or null for every status the caller may see
 * @param nameContains case-insensitive name search, or null
 */
public record ListCoursesQuery(int first, String after, CourseStatus status, String nameContains) {
    public static final int DEFAULT_FIRST = 20;
    public static final int MAX_FIRST = 100;

    public ListCoursesQuery {
        first = first <= 0 ? DEFAULT_FIRST : Math.min(first, MAX_FIRST);
        nameContains = nameContains == null || nameContains.isBlank() ? null : nameContains.trim();
        after = after == null || after.isBlank() ? null : after;
    }
}
