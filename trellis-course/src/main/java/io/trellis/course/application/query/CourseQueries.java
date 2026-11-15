package io.trellis.course.application.query;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.course.api.error.CourseErrors;
import io.trellis.course.api.security.CoursePermissions;
import io.trellis.course.application.port.CourseRepository;
import io.trellis.course.application.view.CourseSummary;
import io.trellis.course.application.view.CourseView;
import io.trellis.course.application.view.CourseViews;
import io.trellis.course.domain.value.CourseCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read use cases of the Course aggregate (decision T16), shared by REST and GraphQL: read-only transactions that
 * return views. The guard is here (operation permission plus {@link CourseAccess}), never in a resolver or
 * controller, so both APIs enforce the same rules.
 */
@Service
public class CourseQueries {
    private final CourseRepository courses;
    private final CourseAccess access;

    public CourseQueries(CourseRepository courses, CourseAccess access) {
        this.courses = courses;
        this.access = access;
    }

    @RequiresPermission(anyOf = {CoursePermissions.READ, CoursePermissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "course.get")
    public CourseView get(RequestContext ctx, String code) {
        CourseCode parsed = CourseCode.parseOrNotFound(code);
        return courses.findByCode(parsed)
                .map(c -> access.requireVisible(ctx, c))
                .map(CourseViews::view)
                .orElseThrow(() -> CourseErrors.notFound(parsed.value()));
    }

    @RequiresPermission(anyOf = {CoursePermissions.READ, CoursePermissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "course.list")
    public CursorPage<CourseSummary> list(RequestContext ctx, ListCoursesQuery query) {
        return courses.page(access.listFilter(ctx, query), query.after(), query.first())
                .map(CourseViews::summary);
    }
}
