package io.trellis.learningcontent.application.course.view;

import io.trellis.learningcontent.domain.course.model.Course;

/** Maps the course aggregate to its summary and detail representations. */
public final class CourseViews {
  private CourseViews() {}

  public static CourseView view(Course c) {
    return new CourseView(
        c.code().value(),
        c.details().name(),
        c.details().description(),
        c.status(),
        c.version(),
        c.createdAt(),
        c.updatedAt(),
        c.activatedAt(),
        c.archivedAt());
  }

  public static CourseSummary summary(Course c) {
    return new CourseSummary(
        c.code().value(), c.details().name(), c.status(), c.createdAt(), c.activatedAt());
  }
}
