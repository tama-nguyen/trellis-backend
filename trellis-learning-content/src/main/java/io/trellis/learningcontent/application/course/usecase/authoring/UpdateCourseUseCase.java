package io.trellis.learningcontent.application.course.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.learningcontent.api.error.CourseErrorCode;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.support.CourseLoader;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.application.course.view.CourseViews;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits a course the client has read, refusing if it moved underneath them. */
@Service
public class UpdateCourseUseCase {
  static final String AUDIT_ACTION = "course.updated";

  private final CourseRepository courses;
  private final CourseLoader loader;
  private final ValidationPipeline<CourseDetails> validation;
  private final Clock clock;

  public UpdateCourseUseCase(
      CourseRepository courses,
      CourseLoader loader,
      List<CommandValidator<CourseDetails>> validators,
      Clock clock) {
    this.courses = courses;
    this.loader = loader;
    this.validation = new ValidationPipeline<>(validators);
    this.clock = clock;
  }

  /**
   * @param expectedVersion the version the client edited (from {@code If-Match}); a different
   *     current version is 412, a concurrent writer between our read and write is 409
   */
  @RequiresPermission(anyOf = CoursePermissions.MANAGE)
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "course.update")
  public CourseView execute(
      RequestContext ctx, String code, long expectedVersion, CourseDetails details) {
    Course current = loader.loadManageable(ctx, code);
    if (current.version() != expectedVersion) {
      throw new TrellisException(
          CourseErrorCode.COURSE_VERSION_MISMATCH,
          "Course " + code + " is at version " + current.version() + ", not " + expectedVersion);
    }
    validation.validate(ctx, details);
    Course next = current.edit(details, clock.instant());
    courses.update(next);
    return CourseViews.view(next);
  }
}
