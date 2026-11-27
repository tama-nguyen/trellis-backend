package io.trellis.notification.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.security.SecurityAction;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notification.api.error.NotificationErrorCode;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.application.support.NotificationLoader;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.notification.application.view.NotificationViews;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.model.NotificationContent;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits a notification the client has read, refusing if it moved underneath them. */
@Service
@RequiredArgsConstructor
public class UpdateNotificationUseCase {
  static final String AUDIT_ACTION = "notification.updated";

  private final NotificationRepository notificationRepository;
  private final NotificationLoader notificationLoader;
  private final ValidationPipeline<NotificationContent> validationPipeline;
  private final Clock clock;

  /**
   * @param expectedVersion the version the client edited (from {@code If-Match}); a different
   *     current version is 412, a concurrent writer between our read and write is 409
   */
  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NotificationPermissions.MANAGE)
  @Transactional
  @Audited(
      action = AUDIT_ACTION,
      aggregateType = Notification.AGGREGATE_TYPE,
      aggregateId = "#code")
  @Observed(name = "notification.update")
  public NotificationView execute(
      final RequestContext ctx,
      final String code,
      final long expectedVersion,
      final NotificationContent content) {
    final Notification currentNotification = notificationLoader.loadVisible(ctx, code);
    if (currentNotification.version() != expectedVersion) {
      throw new TrellisException(
          NotificationErrorCode.NOTIFICATION_VERSION_MISMATCH,
          "Notification "
              + code
              + " is at version "
              + currentNotification.version()
              + ", not "
              + expectedVersion);
    }

    validationPipeline.validate(ctx, content);

    final Notification updatedNotification = currentNotification.edit(content, clock.instant());
    notificationRepository.update(updatedNotification);

    return NotificationViews.view(updatedNotification);
  }
}
