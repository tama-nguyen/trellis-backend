package io.trellis.notification.application.support;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.common.security.ResourceScope;
import io.trellis.notification.api.error.NotificationErrors;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationFilter;
import io.trellis.notification.application.usecase.reading.ListNotificationsQuery;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.policy.NotificationReader;
import io.trellis.notification.domain.policy.NotificationVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies the domain's {@link NotificationVisibility} rule to a request: resolves the caller into a
 * {@link NotificationReader} through the one {@link AuthorizationService} (explicit calls, because
 * the answer depends on the loaded notification), then checks a notification or builds the list
 * filter. Used by queries and by commands that load a notification.
 */
@Component
@RequiredArgsConstructor
public class NotificationAccess {
  private static final PermissionRequirement MANAGE =
      PermissionRequirement.anyOf(NotificationPermissions.MANAGE);
  private static final PermissionRequirement READ_STAFF =
      PermissionRequirement.anyOf(
          NotificationPermissions.READ_STAFF, NotificationPermissions.MANAGE);

  private final AuthorizationService authorizationService;

  public NotificationReader reader(final RequestContext ctx) {
    final String userId = ctx.actor() instanceof Actor.UserActor user ? user.id() : null;
    return new NotificationReader(
        userId,
        authorizationService.decide(ctx, MANAGE, ResourceScope.global()).allowed(),
        authorizationService.decide(ctx, READ_STAFF, ResourceScope.global()).allowed());
  }

  /**
   * @throws io.trellis.common.error.NotFoundException (never 403) when the caller may not see the
   *     notification
   */
  public Notification requireVisible(final RequestContext ctx, final Notification notification) {
    if (!NotificationVisibility.canSee(reader(ctx), notification)) {
      throw NotificationErrors.notFound(notification.code().value());
    }
    return notification;
  }

  public NotificationFilter listFilter(
      final RequestContext ctx, final ListNotificationsQuery query) {
    final NotificationReader reader = reader(ctx);
    return new NotificationFilter(
        reader.manager(),
        NotificationVisibility.READER_STATUSES,
        NotificationVisibility.audiencesFor(reader),
        reader.userId(),
        query.audience(),
        query.titleContains());
  }
}
