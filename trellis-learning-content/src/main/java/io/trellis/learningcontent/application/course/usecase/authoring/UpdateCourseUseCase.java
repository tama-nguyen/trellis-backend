package io.trellis.learningcontent.application.course.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.security.SecurityAction;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.learningcontent.api.error.CourseErrorCode;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.support.CourseLoader;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.application.course.view.CourseViews;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits a course the client has read, refusing if it moved underneath them. */
@Service
@RequiredArgsConstructor
public class UpdateCourseUseCase {
  static final String AUDIT_ACTION = "course.updated";

  private final CourseRepository courseRepository;
  private final CourseLoader courseLoader;
  private final ValidationPipeline<CourseDetails> validationPipeline;
  private final Clock clock;

  /**
   * @param expectedVersion the version the client edited (from {@code If-Match}); a different
   *     current version is 412, a concurrent writer between our read and write is 409
   */
  @SecurityAudited(value = SecurityAction.COURSE_ACCESS, recordSuccess = false)
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "course.update")
  public CourseView execute(
      final RequestContext ctx,
      final String code,
      final long expectedVersion,
      final CourseDetails details) {
    final Course currentCourse = courseLoader.loadManageable(ctx, code);
    if (currentCourse.version() != expectedVersion) {
      throw new TrellisException(
          CourseErrorCode.COURSE_VERSION_MISMATCH,
          "Course "
              + code
              + " is at version "
              + currentCourse.version()
              + ", not "
              + expectedVersion);
    }

    validationPipeline.validate(ctx, details);

    final Course updatedCourse = currentCourse.edit(details, clock.instant());
    courseRepository.update(updatedCourse);

    return CourseViews.view(updatedCourse);
  }
}
