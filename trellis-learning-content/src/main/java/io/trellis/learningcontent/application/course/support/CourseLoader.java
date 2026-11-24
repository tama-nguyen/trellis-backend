package io.trellis.learningcontent.application.course.support;

import io.trellis.common.context.RequestContext;
import io.trellis.learningcontent.api.error.CourseErrors;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads a course the caller is allowed to see, or reports it missing.
 *
 * <p>A collaborator, not a use case: most of them start this way, and they must agree on what "not found"
 * means. A caller who may not see a course gets the same answer as one asking about a course that does not
 * exist, so the error cannot be used to enumerate them.
 */
@Service
@RequiredArgsConstructor
public class CourseLoader {

    private final CourseRepository courses;
    private final CourseAccess access;

    public Course loadVisible(RequestContext ctx, String code) {
        CourseCode parsed = CourseCode.parseOrNotFound(code);
        return access.requireVisible(
                ctx, courses.findByCode(parsed).orElseThrow(() -> CourseErrors.notFound(parsed.value())));
    }

    /** Loads it and refuses unless the caller may change this one; a scoped grant is enough. */
    public Course loadManageable(RequestContext ctx, String code) {
        return access.requireManageable(ctx, loadVisible(ctx, code));
    }
}
