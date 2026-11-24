package io.trellis.learningcontent.application.course.port;

import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.platform.security.authz.GrantSubject;
import java.util.Set;

/**
 * What a list may return. Visibility is part of the query, not a filter after it, so pages are full
 * and the cursor never skips rows.
 *
 * <p>Each visibility field is an independent alternative, OR-ed together by the repository: a null
 * or empty one simply does not apply. Keeping them independent is deliberate - cross-dependent
 * flags would multiply the SQL branches every time a rule is added.
 *
 * @param unrestricted a global manager sees everything; the alternatives below are then ignored
 * @param visibleStatuses statuses anyone may see; empty when the caller may not read courses at all
 * @param ownerId the caller's own courses; null when that alternative does not apply
 * @param grantSubject the caller as a holder of scoped grants; null for system and anonymous
 *     callers
 * @param status client filter, or null
 * @param nameContains case-insensitive name search, or null
 */
public record CourseFilter(
    boolean unrestricted,
    Set<CourseStatus> visibleStatuses,
    String ownerId,
    GrantSubject grantSubject,
    CourseStatus status,
    String nameContains) {
  public CourseFilter {
    visibleStatuses = Set.copyOf(visibleStatuses);
  }
}
