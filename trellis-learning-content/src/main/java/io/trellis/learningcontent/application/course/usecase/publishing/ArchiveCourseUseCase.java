package io.trellis.learningcontent.application.course.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.context.RequestContext;
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

  private final CourseRepository courses;
  private final CourseLoader loader;
  private final Clock clock;

  @Transactional
  @Audited(action = AUDIT_ACTION, aggregateType = Course.AGGREGATE_TYPE, aggregateId = "#code")
  @Observed(name = "course.archive")
  public CourseView execute(RequestContext ctx, String code) {
    Course next = loader.loadManageable(ctx, code).archive(clock.instant());
    courses.update(next);
    return CourseViews.view(next);
  }
}
