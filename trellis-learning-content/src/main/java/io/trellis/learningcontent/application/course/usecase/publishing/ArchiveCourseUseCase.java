package io.trellis.learningcontent.application.course.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.support.CourseLoader;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.learningcontent.application.course.view.CourseViews;
import io.trellis.learningcontent.domain.course.model.Course;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archive, not delete: the row stays for the audit trail and for anything that still references the
 * code.
 */
@Service
@RequiredArgsConstructor
public class ArchiveCourseUseCase {
  static final String AUDIT_ACTION = "course.archived";

  private final CourseRepository courseRepository;
  private final CourseLoader courseLoader;
  private final Clock clock;

  @SecurityAudited(value = SecurityAction.COURSE_ACCESS, recordSuccess = false)
  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "course.archive")
  public CourseView execute(final RequestContext ctx, final String code) {
    final Course currentCourse = courseLoader.loadManageable(ctx, code);
    final Course archivedCourse = currentCourse.archive(clock.instant());

    courseRepository.update(archivedCourse);

    return CourseViews.view(archivedCourse);
  }
}
