package io.trellis.notification.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.application.support.NotificationLoader;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.notification.application.view.NotificationViews;
import io.trellis.notification.domain.model.Notification;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes a notification out of circulation without deleting it; the audit trail still needs the row.
 */
@Service
@RequiredArgsConstructor
public class ArchiveNotificationUseCase {
  static final String AUDIT_ACTION = "notification.archived";

  private final NotificationRepository notificationRepository;
  private final NotificationLoader notificationLoader;
  private final Clock clock;

  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NotificationPermissions.MANAGE)
  @Transactional
  @Audited(
      action = AUDIT_ACTION,
      aggregateType = Notification.AGGREGATE_TYPE,
      aggregateId = "#code")
  @Observed(name = "notification.archive")
  public NotificationView execute(final RequestContext ctx, final String code) {
    final Notification currentNotification = notificationLoader.loadVisible(ctx, code);
    final Notification archivedNotification = currentNotification.archive(clock.instant());

    notificationRepository.update(archivedNotification);

    return NotificationViews.view(archivedNotification);
  }
}
