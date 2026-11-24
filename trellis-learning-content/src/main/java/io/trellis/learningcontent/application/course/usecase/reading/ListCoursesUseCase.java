package io.trellis.learningcontent.application.course.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.application.course.support.CourseAccess;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import io.trellis.learningcontent.application.course.view.CourseViews;
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

  private final CourseRepository courses;
  private final CourseAccess access;

  @Transactional(readOnly = true)
  @Observed(name = "course.list")
  public CursorPage<CourseSummary> execute(RequestContext ctx, ListCoursesQuery query) {
    return courses
        .page(access.listFilter(ctx, query), query.after(), query.first())
        .map(CourseViews::summary);
  }
}
