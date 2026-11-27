package io.trellis.notification.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.SecurityAction;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationFilter;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.application.support.NotificationAccess;
import io.trellis.notification.application.view.NotificationSummary;
import io.trellis.notification.application.view.NotificationViews;
import io.trellis.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One keyset page. Visibility is part of the query, so a page is never short of rows the caller may
 * see.
 */
@Service
@RequiredArgsConstructor
public class ListNotificationsUseCase {

  private final NotificationRepository notificationRepository;
  private final NotificationAccess notificationAccess;

  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = {NotificationPermissions.READ, NotificationPermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notification.list")
  public CursorPage<NotificationSummary> execute(
      final RequestContext ctx, final ListNotificationsQuery query) {
    final NotificationFilter notificationFilter = notificationAccess.listFilter(ctx, query);
    final CursorPage<Notification> notificationPage =
        notificationRepository.page(notificationFilter, query.after(), query.first());

    return notificationPage.map(NotificationViews::summary);
  }
}
