package io.trellis.learningcontent.infrastructure.persistence;

import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.learningcontent.api.security.CourseScopes;
import io.trellis.learningcontent.application.course.port.CourseFilter;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.FilteredSelect;
import io.trellis.platform.security.authz.GrantSubject;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.sql.GrantPredicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which courses the list may return: who may see a row, then the client's filters. The visibility
 * alternatives are independent and OR-ed together (see {@link CourseFilter}).
 */
final class CourseListQuery {
  private static final String SELECT = "SELECT c.* FROM course c WHERE TRUE";
  private static final String ID_COLUMN = "c.id";
  private static final String HAS_VISIBLE_STATUS = "c.status IN (:visibleStatuses)";
  private static final String OWNED_BY_CALLER = "c.owner_id = :ownerId";
  private static final String ACTIVE_OR_OWN = "(c.status = :readStatus OR c.owner_id = :readOwner)";
  private static final String NOTHING_VISIBLE = "FALSE";
  private static final String HAS_STATUS = "c.status = :status";
  private static final String NAME_CONTAINS = "c.name ILIKE :namePattern ESCAPE '\\'";
  // MANAGE and READ appear in the same statement, so their grant parameters need distinct names.
  private static final String READ_GRANT_PARAMETERS = "read";

  private CourseListQuery() {}

  static FilteredSelect of(final CourseFilter filter) {
    final FilteredSelect select = FilteredSelect.from(SELECT);
    if (!filter.unrestricted()) {
      restrictToVisible(select, filter);
    }
    return select
        .andIfPresent(HAS_STATUS, "status", filter.status(), CourseStatus::name)
        .andIfPresent(
            NAME_CONTAINS, "namePattern", filter.nameContains(), JdbcSupport::containsPattern);
  }

  private static void restrictToVisible(final FilteredSelect select, final CourseFilter filter) {
    final List<String> alternatives = new ArrayList<>();
    if (!filter.visibleStatuses().isEmpty()) {
      alternatives.add(hasVisibleStatus(select, filter.visibleStatuses()));
    }
    if (filter.ownerId() != null) {
      alternatives.add(ownedBy(select, filter.ownerId()));
    }
    if (filter.grantSubject() != null) {
      alternatives.add(manageableByGrant(select, filter.grantSubject()));
      alternatives.add(readableByGrant(select, filter.grantSubject()));
    }

    select.and(
        alternatives.isEmpty() ? NOTHING_VISIBLE : "(" + String.join(" OR ", alternatives) + ")");
  }

  private static String hasVisibleStatus(
      final FilteredSelect select, final Set<CourseStatus> statuses) {
    select.bind("visibleStatuses", statuses.stream().map(CourseStatus::name).toList());
    return HAS_VISIBLE_STATUS;
  }

  private static String ownedBy(final FilteredSelect select, final String ownerId) {
    select.bind("ownerId", ownerId);
    return OWNED_BY_CALLER;
  }

  private static String manageableByGrant(final FilteredSelect select, final GrantSubject subject) {
    return bind(
        select,
        GrantPredicate.coveredByGrant(
            ID_COLUMN, CoursePermissions.MANAGE, CourseScopes.COURSE, subject));
  }

  /** Scoped READ obeys the same ACTIVE-or-owner rule as the detail guard. */
  private static String readableByGrant(final FilteredSelect select, final GrantSubject subject) {
    final String granted =
        bind(
            select,
            GrantPredicate.coveredByGrant(
                ID_COLUMN,
                CoursePermissions.READ,
                CourseScopes.COURSE,
                subject,
                READ_GRANT_PARAMETERS));
    // Only a user can own a course; null makes the owner comparison match nothing.
    final String owner = subject.type() == GrantSubjectType.USER ? subject.id() : null;
    select.bind("readStatus", CourseStatus.ACTIVE.name()).bind("readOwner", owner);

    return "(" + granted + " AND " + ACTIVE_OR_OWN + ")";
  }

  private static String bind(final FilteredSelect select, final GrantPredicate.Fragment fragment) {
    fragment.params().forEach(select::bind);
    return fragment.sql();
  }
}
