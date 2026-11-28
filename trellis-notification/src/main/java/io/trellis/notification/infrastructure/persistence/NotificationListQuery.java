package io.trellis.notification.infrastructure.persistence;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationStatus;
import io.trellis.notification.application.port.NotificationFilter;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.FilteredSelect;

/**
 * Which notifications the list may return: who may see a row, then the client's filters (see {@link
 * NotificationFilter}).
 */
final class NotificationListQuery {
  private static final String SELECT = "SELECT n.* FROM notification n WHERE TRUE";
  private static final String PUBLISHED_TO_CALLER =
      "(n.status IN (:visibleStatuses) AND n.audience IN (:visibleAudiences))";
  private static final String PUBLISHED_TO_CALLER_OR_OWN =
      "(" + PUBLISHED_TO_CALLER + " OR n.author_id = :ownerId)";
  private static final String HAS_AUDIENCE = "n.audience = :audience";
  private static final String TITLE_CONTAINS = "n.title ILIKE :titlePattern ESCAPE '\\'";

  private NotificationListQuery() {}

  static FilteredSelect of(final NotificationFilter filter) {
    final FilteredSelect select = FilteredSelect.from(SELECT);
    if (!filter.unrestricted()) {
      restrictToVisible(select, filter);
    }
    return select
        .andIfPresent(HAS_AUDIENCE, "audience", filter.audience(), NotificationAudience::name)
        .andIfPresent(
            TITLE_CONTAINS, "titlePattern", filter.titleContains(), JdbcSupport::containsPattern);
  }

  private static void restrictToVisible(
      final FilteredSelect select, final NotificationFilter filter) {
    select
        .bind(
            "visibleStatuses",
            filter.visibleStatuses().stream().map(NotificationStatus::name).toList())
        .bind(
            "visibleAudiences",
            filter.visibleAudiences().stream().map(NotificationAudience::name).toList());

    if (filter.ownerId() == null) {
      select.and(PUBLISHED_TO_CALLER);
    } else {
      select.and(PUBLISHED_TO_CALLER_OR_OWN).bind("ownerId", filter.ownerId());
    }
  }
}
