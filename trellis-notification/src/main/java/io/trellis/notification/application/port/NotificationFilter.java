package io.trellis.notification.application.port;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationStatus;
import java.util.Set;

/**
 * What a list may return. Visibility is part of the query, not a filter after it, so pages are full
 * and the cursor never skips rows: {@code (status IN visibleStatuses AND audience IN
 * visibleAudiences) OR author = ownerId}, then narrowed by the client's own {@code audience} and
 * {@code titleContains}.
 *
 * @param unrestricted managers see everything; the visibility sets are ignored
 * @param ownerId the caller's own notifications are always visible to them; null for non-user
 *     actors
 * @param audience client filter, or null
 * @param titleContains case-insensitive title search, or null
 */
public record NotificationFilter(
    boolean unrestricted,
    Set<NotificationStatus> visibleStatuses,
    Set<NotificationAudience> visibleAudiences,
    String ownerId,
    NotificationAudience audience,
    String titleContains) {
  public NotificationFilter {
    visibleStatuses = Set.copyOf(visibleStatuses);
    visibleAudiences = Set.copyOf(visibleAudiences);
  }
}
