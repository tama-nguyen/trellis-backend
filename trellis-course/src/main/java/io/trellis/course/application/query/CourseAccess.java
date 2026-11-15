package io.trellis.course.application.query;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.course.api.error.CourseErrors;
import io.trellis.course.api.security.CoursePermissions;
import io.trellis.course.application.port.CourseFilter;
import io.trellis.course.domain.model.Course;
import io.trellis.course.domain.policy.CourseReader;
import io.trellis.course.domain.policy.CourseVisibility;
import org.springframework.stereotype.Component;

/**
 * Applies the domain's {@link CourseVisibility} rule to a request: resolves the caller into a
 * {@link CourseReader} through the one {@link AuthorizationService} (an explicit call, because the answer
 * depends on the loaded row, which an annotation cannot see), then checks one course or builds the list filter.
 * Used by the queries and by the commands that load a course.
 */
@Component
public class CourseAccess {
    private static final PermissionRequirement MANAGE = PermissionRequirement.anyOf(CoursePermissions.MANAGE);

    private final AuthorizationService authorization;

    public CourseAccess(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    public CourseReader reader(RequestContext ctx) {
        String userId = ctx.actor() instanceof Actor.UserActor user ? user.id() : null;
        return new CourseReader(userId, authorization.decide(ctx, MANAGE).allowed());
    }

    /** @throws io.trellis.common.error.NotFoundException (never 403) when the caller may not see it */
    public Course requireVisible(RequestContext ctx, Course course) {
        if (!CourseVisibility.canSee(reader(ctx), course)) {
            throw CourseErrors.notFound(course.code().value());
        }
        return course;
    }

    public CourseFilter listFilter(RequestContext ctx, ListCoursesQuery query) {
        CourseReader reader = reader(ctx);
        return new CourseFilter(
                reader.manager(),
                CourseVisibility.READER_STATUSES,
                reader.userId(),
                query.status(),
                query.nameContains());
    }
}
