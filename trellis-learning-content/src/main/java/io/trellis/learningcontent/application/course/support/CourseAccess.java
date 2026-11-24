package io.trellis.learningcontent.application.course.support;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.NotFoundException;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.common.security.ResourceScope;
import io.trellis.learningcontent.api.error.CourseErrors;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.learningcontent.api.security.CourseScopes;
import io.trellis.learningcontent.application.course.port.CourseFilter;
import io.trellis.learningcontent.application.course.usecase.reading.ListCoursesQuery;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.policy.CourseReader;
import io.trellis.learningcontent.domain.course.policy.CourseVisibility;
import io.trellis.platform.security.authz.GrantSubject;
import java.time.Clock;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies the domain's {@link CourseVisibility} rule to a request: resolves the caller into a
 * {@link CourseReader} through the one {@link AuthorizationService} (an explicit call, because the
 * answer depends on the loaded row, which an annotation cannot see), then checks one course or
 * builds the list filter. Used by the queries and by the commands that load a course.
 */
@Component
@RequiredArgsConstructor
public class CourseAccess {
  private static final PermissionRequirement MANAGE =
      PermissionRequirement.anyOf(CoursePermissions.MANAGE);
  private static final PermissionRequirement VIEW =
      PermissionRequirement.anyOf(CoursePermissions.READ, CoursePermissions.MANAGE);

  private final AuthorizationService authorizationService;
  private final Clock clock;

  public CourseReader reader(final RequestContext ctx) {
    final String userId = ctx.actor() instanceof Actor.UserActor user ? user.id() : null;
    return new CourseReader(
        userId, authorizationService.decide(ctx, MANAGE, ResourceScope.global()).allowed());
  }

  /**
   * @throws NotFoundException (never 403) when the caller may not see it
   */
  public Course requireVisible(final RequestContext ctx, final Course course) {
    final ResourceScope scope = scope(course);
    final boolean manageable = authorizationService.decide(ctx, MANAGE, scope).allowed();
    final boolean readable = authorizationService.decide(ctx, VIEW, scope).allowed();
    if (!manageable && (!readable || !CourseVisibility.canSee(reader(ctx), course))) {
      throw CourseErrors.notFound(course.code().value());
    }
    return course;
  }

  public Course requireManageable(final RequestContext ctx, final Course course) {
    authorizationService.require(ctx, MANAGE, scope(course));
    return course;
  }

  public CourseFilter listFilter(final RequestContext ctx, final ListCoursesQuery query) {
    final CourseReader reader = reader(ctx);
    final boolean standardVisibility =
        authorizationService.decide(ctx, VIEW, ResourceScope.global()).allowed();
    return new CourseFilter(
        reader.manager(),
        standardVisibility ? CourseVisibility.READER_STATUSES : Set.of(),
        standardVisibility ? reader.userId() : null,
        GrantSubject.of(ctx.actor(), clock.instant()).orElse(null),
        query.status(),
        query.nameContains());
  }

  private static ResourceScope scope(final Course course) {
    return ResourceScope.of(CourseScopes.COURSE, course.id().toString());
  }
}
