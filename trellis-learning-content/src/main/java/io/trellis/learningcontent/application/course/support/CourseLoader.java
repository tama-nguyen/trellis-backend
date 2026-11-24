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
 * <p>A collaborator, not a use case: most of them start this way, and they must agree on what "not
 * found" means. A caller who may not see a course gets the same answer as one asking about a course
 * that does not exist, so the error cannot be used to enumerate them.
 */
@Service
@RequiredArgsConstructor
public class CourseLoader {

  private final CourseRepository courseRepository;
  private final CourseAccess courseAccess;

  public Course loadVisible(final RequestContext ctx, final String code) {
    final CourseCode courseCode = CourseCode.parseOrNotFound(code);
    final Course course =
        courseRepository
            .findByCode(courseCode)
            .orElseThrow(() -> CourseErrors.notFound(courseCode.value()));

    return courseAccess.requireVisible(ctx, course);
  }

  /** Loads it and refuses unless the caller may change this one; a scoped grant is enough. */
  public Course loadManageable(final RequestContext ctx, final String code) {
    final Course course = loadVisible(ctx, code);
    return courseAccess.requireManageable(ctx, course);
  }
}
