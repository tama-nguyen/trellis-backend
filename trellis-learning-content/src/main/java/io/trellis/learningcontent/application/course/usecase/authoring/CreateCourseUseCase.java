package io.trellis.learningcontent.application.course.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.security.SecurityAction;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.application.course.view.CourseViews;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft course owned by the caller. */
@Service
@RequiredArgsConstructor
public class CreateCourseUseCase {
  static final String AUDIT_ACTION = "course.created";

  private final CourseRepository courseRepository;
  private final ValidationPipeline<CourseDetails> validationPipeline;
  private final Clock clock;
  private final ReferenceCodeGenerator courseCodeGenerator =
      new ReferenceCodeGenerator(CourseCode.PREFIX);

  @SecurityAudited(value = SecurityAction.COURSE_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = CoursePermissions.MANAGE)
  @Transactional
  @Audited(
      action = AUDIT_ACTION,
      aggregateType = Course.AGGREGATE_TYPE,
      aggregateId = "#result.code()")
  @Observed(name = "course.create")
  public CourseView execute(final RequestContext ctx, final CourseDetails details) {
    validationPipeline.validate(ctx, details);

    final Course.Draft draft =
        Course.draft(
            CourseCode.generate(courseCodeGenerator), details, ctx.actor().id(), clock.instant());

    final Course draftCourse = courseRepository.create(draft);

    return CourseViews.view(draftCourse);
  }
}
