package io.trellis.notice.application.support;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.common.security.ResourceScope;
import io.trellis.notice.api.error.NoticeErrors;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeFilter;
import io.trellis.notice.application.usecase.reading.ListNoticesQuery;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.policy.NoticeReader;
import io.trellis.notice.domain.policy.NoticeVisibility;
import org.springframework.stereotype.Component;

/**
 * Applies the domain's {@link NoticeVisibility} rule to a request: resolves the caller into a
 * {@link NoticeReader} through the one {@link AuthorizationService} (explicit calls, because the
 * answer depends on the loaded notice), then checks a notice or builds the list filter. Used by
 * queries and by commands that load a notice.
 */
@Component
public class NoticeAccess {
  private static final PermissionRequirement MANAGE =
      PermissionRequirement.anyOf(NoticePermissions.MANAGE);
  private static final PermissionRequirement READ_STAFF =
      PermissionRequirement.anyOf(NoticePermissions.READ_STAFF, NoticePermissions.MANAGE);

  private final AuthorizationService authorization;

  public NoticeAccess(AuthorizationService authorization) {
    this.authorization = authorization;
  }

  public NoticeReader reader(RequestContext ctx) {
    String userId = ctx.actor() instanceof Actor.UserActor user ? user.id() : null;
    return new NoticeReader(
        userId,
        authorization.decide(ctx, MANAGE, ResourceScope.global()).allowed(),
        authorization.decide(ctx, READ_STAFF, ResourceScope.global()).allowed());
  }

  /**
   * @throws io.trellis.common.error.NotFoundException (never 403) when the caller may not see the
   *     notice
   */
  public Notice requireVisible(RequestContext ctx, Notice notice) {
    if (!NoticeVisibility.canSee(reader(ctx), notice)) {
      throw NoticeErrors.notFound(notice.code().value());
    }
    return notice;
  }

  public NoticeFilter listFilter(RequestContext ctx, ListNoticesQuery query) {
    NoticeReader reader = reader(ctx);
    return new NoticeFilter(
        reader.manager(),
        NoticeVisibility.READER_STATUSES,
        NoticeVisibility.audiencesFor(reader),
        reader.userId(),
        query.audience(),
        query.titleContains());
  }
}
