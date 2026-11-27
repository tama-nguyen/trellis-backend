package io.trellis.notification.application.port;

import io.trellis.common.page.CursorPage;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.value.NotificationCode;
import java.util.Optional;

/**
 * Port to the notification table. Implementations run inside the caller's transaction and never
 * open one.
 */
public interface NotificationRepository {
  Optional<Notification> findByCode(NotificationCode code);

  void insert(Notification notification);

  /**
   * Writes {@code next} if the stored row still has {@code next.version() - 1}.
   *
   * @throws io.trellis.common.error.ConcurrentModificationException when another writer won the
   *     race
   */
  void update(Notification next);

  /**
   * One keyset page, newest first. {@code after} is the opaque cursor of the previous page, or
   * null.
   */
  CursorPage<Notification> page(NotificationFilter filter, String after, int limit);
}
