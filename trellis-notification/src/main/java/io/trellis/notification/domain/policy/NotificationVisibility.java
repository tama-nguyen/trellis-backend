package io.trellis.notification.domain.policy;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationStatus;
import io.trellis.notification.domain.model.Notification;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who may see a notification:
 *
 * <ul>
 *   <li>a manager sees every notification in every status;
 *   <li>everyone else sees PUBLISHED notifications for ALL, plus STAFF ones as a staff reader;
 *   <li>authors always see their own notifications.
 * </ul>
 *
 * A notification the reader may not see must be reported exactly like a missing one (404), so its
 * existence leaks nothing. List queries apply the same rule in SQL ({@code NotificationFilter}).
 */
public final class NotificationVisibility {
  /** The only status a non-manager sees (besides their own notifications). */
  public static final Set<NotificationStatus> READER_STATUSES =
      EnumSet.of(NotificationStatus.PUBLISHED);

  private NotificationVisibility() {}

  public static boolean canSee(NotificationReader reader, Notification notification) {
    if (reader.manager()
        || (reader.userId() != null && notification.isAuthoredBy(reader.userId()))) {
      return true;
    }
    return READER_STATUSES.contains(notification.status())
        && audiencesFor(reader).contains(notification.content().audience());
  }

  public static Set<NotificationAudience> audiencesFor(NotificationReader reader) {
    return reader.staffReader()
        ? EnumSet.allOf(NotificationAudience.class)
        : EnumSet.of(NotificationAudience.ALL);
  }
}
