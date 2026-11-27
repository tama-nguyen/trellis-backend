package io.trellis.notification.application.support;

import io.trellis.common.context.RequestContext;
import io.trellis.notification.api.error.NotificationErrors;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.value.NotificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads a notification the caller is allowed to see, or reports it missing.
 *
 * <p>A collaborator, not a use case: four of them start this way, and they must agree on what "not
 * found" means. A caller who may not see a notification has to get the same answer as one asking
 * about a notification that does not exist, or the error itself becomes a way to enumerate them.
 */
@Service
@RequiredArgsConstructor
public class NotificationLoader {

  private final NotificationRepository notificationRepository;
  private final NotificationAccess notificationAccess;

  public Notification loadVisible(final RequestContext ctx, final String code) {
    final NotificationCode notificationCode = NotificationCode.parseOrNotFound(code);
    final Notification notification =
        notificationRepository
            .findByCode(notificationCode)
            .orElseThrow(() -> NotificationErrors.notFound(notificationCode.value()));

    return notificationAccess.requireVisible(ctx, notification);
  }
}
