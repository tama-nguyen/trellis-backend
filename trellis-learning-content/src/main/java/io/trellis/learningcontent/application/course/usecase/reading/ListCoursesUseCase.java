package io.trellis.learningcontent.application.course.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.common.security.SecurityAction;
import io.trellis.learningcontent.application.course.port.CourseFilter;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.support.CourseAccess;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import io.trellis.learningcontent.application.course.view.CourseViews;
import io.trellis.learningcontent.domain.course.model.Course;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One keyset page. Visibility - including courses reachable only through a scoped grant - is part
 * of the query, so a page is never short of rows the caller may see.
 */
@Service
@RequiredArgsConstructor
public class ListCoursesUseCase {

  private final CourseRepository courseRepository;
  private final CourseAccess courseAccess;

  @SecurityAudited(value = SecurityAction.COURSE_ACCESS, recordSuccess = false)
  @Transactional(readOnly = true)
  @Observed(name = "course.list")
  public CursorPage<CourseSummary> execute(final RequestContext ctx, final ListCoursesQuery query) {
    final CourseFilter courseFilter = courseAccess.listFilter(ctx, query);
    final CursorPage<Course> coursePage =
        courseRepository.page(courseFilter, query.after(), query.first());

    return coursePage.map(CourseViews::summary);
  }
}
