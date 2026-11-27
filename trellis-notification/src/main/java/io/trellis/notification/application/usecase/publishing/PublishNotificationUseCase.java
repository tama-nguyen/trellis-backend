package io.trellis.notification.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.Idempotent;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.notification.api.event.NotificationPublishedV1;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.application.support.NotificationLoader;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.notification.application.view.NotificationViews;
import io.trellis.notification.domain.model.Notification;
import io.trellis.platform.events.outbox.OutboxMessage;
import io.trellis.platform.events.outbox.OutboxWriter;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes and emits {@code notification.published} in the same transaction. With an {@code
 * Idempotency-Key} a retry returns the stored first result instead of failing with "already
 * published".
 *
 * <p>This is the reference stack of the whole base: authorise, replay a stored result for a
 * repeated key, transaction, audit row and outbox row inside it, metrics innermost.
 */
@Service
@RequiredArgsConstructor
public class PublishNotificationUseCase {
  static final String AUDIT_ACTION = "notification.published";

  private final NotificationRepository notificationRepository;
  private final NotificationLoader notificationLoader;
  private final OutboxWriter outboxWriter;
  private final Clock clock;

  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = NotificationPermissions.MANAGE)
  @Idempotent(key = "#idempotencyKey == null ? null : #code + '/' + #idempotencyKey")
  @Transactional
  @Audited(
      action = AUDIT_ACTION,
      aggregateType = Notification.AGGREGATE_TYPE,
      aggregateId = "#code")
  @Observed(name = "notification.publish")
  public NotificationView execute(
      final RequestContext ctx, final String code, final String idempotencyKey) {
    final Notification currentNotification = notificationLoader.loadVisible(ctx, code);
    final Notification publishedNotification =
        currentNotification.publish(ctx.actor().id(), clock.instant());

    notificationRepository.update(publishedNotification);
    final NotificationPublishedV1 notificationPublishedEvent =
        new NotificationPublishedV1(
            publishedNotification.code().value(),
            publishedNotification.content().title(),
            publishedNotification.content().audience(),
            publishedNotification.publishedAt(),
            publishedNotification.publishedBy());
    final OutboxMessage outboxMessage =
        OutboxMessage.of(
                NotificationPublishedV1.EVENT_TYPE,
                Notification.AGGREGATE_TYPE,
                publishedNotification.code().value(),
                notificationPublishedEvent)
            .withVersion(NotificationPublishedV1.EVENT_VERSION);

    outboxWriter.append(ctx, outboxMessage);

    return NotificationViews.view(publishedNotification);
  }
}
