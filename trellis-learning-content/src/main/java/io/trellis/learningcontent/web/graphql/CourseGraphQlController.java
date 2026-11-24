package io.trellis.learningcontent.web.graphql;

import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.learningcontent.application.course.usecase.reading.GetCourseUseCase;
import io.trellis.learningcontent.application.course.usecase.reading.ListCoursesQuery;
import io.trellis.learningcontent.application.course.usecase.reading.ListCoursesUseCase;
import io.trellis.learningcontent.application.course.view.CourseSummary;
import io.trellis.learningcontent.application.course.view.CourseView;
import io.trellis.platform.web.graphql.GraphQlContextKeys;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL read model for the UI (decision T12): {@code graphql/course/course.graphqls}. Thin like
 * the REST controller and calling the same {@link CourseQueries}, which is where authorization
 * happens. A field that would be loaded once per parent row gets a {@code @BatchMapping} instead
 * (see {@code trellis-notice}).
 */
@Controller
class CourseGraphQlController {
  private final GetCourseUseCase getCourse;
  private final ListCoursesUseCase listCourses;

  CourseGraphQlController(GetCourseUseCase getCourse, ListCoursesUseCase listCourses) {
    this.getCourse = getCourse;
    this.listCourses = listCourses;
  }

  @QueryMapping
  CourseView course(
      @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx, @Argument String code) {
    return getCourse.execute(ctx, code);
  }

  @QueryMapping
  CursorPage<CourseSummary> courses(
      @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx,
      @Argument Integer first,
      @Argument String after,
      @Argument CourseStatus status,
      @Argument String query) {
    int size = first == null ? ListCoursesQuery.DEFAULT_FIRST : first;
    return listCourses.execute(ctx, new ListCoursesQuery(size, after, status, query));
  }
}
