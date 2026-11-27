package io.trellis.notification.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.support.NotificationLoader;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.notification.application.view.NotificationViews;
import io.trellis.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one notification. A notification the caller may not see answers 404, never 403. */
@Service
@RequiredArgsConstructor
public class GetNotificationUseCase {

  private final NotificationLoader notificationLoader;

  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = {NotificationPermissions.READ, NotificationPermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notification.get")
  public NotificationView execute(final RequestContext ctx, final String code) {
    final Notification visibleNotification = notificationLoader.loadVisible(ctx, code);
    return NotificationViews.view(visibleNotification);
  }
}
