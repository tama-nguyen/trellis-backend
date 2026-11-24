package io.trellis.learningcontent.application.course.support;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
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
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies the domain's {@link CourseVisibility} rule to a request: resolves the caller into a
 * {@link CourseReader} through the one {@link AuthorizationService} (an explicit call, because the
 * answer depends on the loaded row, which an annotation cannot see), then checks one course or
 * builds the list filter. Used by the queries and by the commands that load a course.
 */
@Component
public class CourseAccess {
  private static final PermissionRequirement MANAGE =
      PermissionRequirement.anyOf(CoursePermissions.MANAGE);
  private static final PermissionRequirement VIEW =
      PermissionRequirement.anyOf(CoursePermissions.READ, CoursePermissions.MANAGE);

  private final AuthorizationService authorization;

  public CourseAccess(AuthorizationService authorization) {
    this.authorization = authorization;
  }

  public CourseReader reader(RequestContext ctx) {
    String userId = ctx.actor() instanceof Actor.UserActor user ? user.id() : null;
    return new CourseReader(
        userId, authorization.decide(ctx, MANAGE, ResourceScope.global()).allowed());
  }

  /**
   * @throws io.trellis.common.error.NotFoundException (never 403) when the caller may not see it
   */
  public Course requireVisible(RequestContext ctx, Course course) {
    ResourceScope scope = scope(course);
    boolean manageable = authorization.decide(ctx, MANAGE, scope).allowed();
    boolean readable = authorization.decide(ctx, VIEW, scope).allowed();
    if (!manageable && (!readable || !CourseVisibility.canSee(reader(ctx), course))) {
      throw CourseErrors.notFound(course.code().value());
    }
    return course;
  }

  public Course requireManageable(RequestContext ctx, Course course) {
    authorization.require(ctx, MANAGE, scope(course));
    return course;
  }

  public CourseFilter listFilter(RequestContext ctx, ListCoursesQuery query) {
    CourseReader reader = reader(ctx);
    boolean standardVisibility = authorization.decide(ctx, VIEW, ResourceScope.global()).allowed();
    return new CourseFilter(
        reader.manager(),
        standardVisibility ? CourseVisibility.READER_STATUSES : Set.of(),
        standardVisibility ? reader.userId() : null,
        GrantSubject.of(ctx.actor(), ctx.requestTime()).orElse(null),
        query.status(),
        query.nameContains());
  }

  private static ResourceScope scope(Course course) {
    return ResourceScope.of(CourseScopes.COURSE, course.id().toString());
  }
}
