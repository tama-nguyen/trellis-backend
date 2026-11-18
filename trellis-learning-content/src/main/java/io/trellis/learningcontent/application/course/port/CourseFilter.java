package io.trellis.learningcontent.application.course.port;

import io.trellis.learningcontent.api.dto.CourseStatus;
import java.util.Set;

/**
 * What a list may return. Visibility is part of the query, not a filter after it, so pages are full and the
 * cursor never skips rows: {@code status IN visibleStatuses OR owner = ownerId}, then narrowed by the client's
 * own filters.
 *
 * @param unrestricted managers see everything; the visibility set is ignored
 * @param ownerId the caller's own courses are always visible to them; null for non-user actors
 * @param status client filter, or null
 * @param nameContains case-insensitive name search, or null
 */
public record CourseFilter(
        boolean unrestricted,
        Set<CourseStatus> visibleStatuses,
        String ownerId,
        CourseStatus status,
        String nameContains) {
    public CourseFilter {
        visibleStatuses = Set.copyOf(visibleStatuses);
    }
}
