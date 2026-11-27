package io.trellis.notification.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.security.SecurityAction;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.notification.application.view.NotificationViews;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.model.NotificationContent;
import io.trellis.notification.domain.value.NotificationCode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft. */
@Service
@RequiredArgsConstructor
public class CreateNotificationUseCase {
  static final String AUDIT_ACTION = "notification.created";

  private final NotificationRepository notificationRepository;
  private final ValidationPipeline<NotificationContent> validationPipeline;
  private final Clock clock;
  private final ReferenceCodeGenerator notificationCodeGenerator =
      new ReferenceCodeGenerator(NotificationCode.PREFIX);

  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NotificationPermissions.MANAGE)
  @Transactional
  @Audited(
      action = AUDIT_ACTION,
      aggregateType = Notification.AGGREGATE_TYPE,
      aggregateId = "#result.code()")
  @Observed(name = "notification.create")
  public NotificationView execute(final RequestContext ctx, final NotificationContent content) {
    validationPipeline.validate(ctx, content);

    final Notification draftNotification =
        Notification.draft(
            UuidV7.next(clock),
            NotificationCode.generate(notificationCodeGenerator),
            content,
            ctx.actor().id(),
            clock.instant());

    notificationRepository.insert(draftNotification);

    return NotificationViews.view(draftNotification);
  }
}
