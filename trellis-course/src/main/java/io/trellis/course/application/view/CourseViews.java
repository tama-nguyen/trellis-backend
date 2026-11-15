package io.trellis.course.application.view;

import io.trellis.course.domain.model.Course;

/** Aggregate to views. Kept in one place so REST and GraphQL can never disagree on a field. */
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
        return new CourseSummary(c.code().value(), c.details().name(), c.status(), c.createdAt(), c.activatedAt());
    }
}
