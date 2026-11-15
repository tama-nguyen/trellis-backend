package io.trellis.course.api.error;

import io.trellis.common.error.NotFoundException;

/** Exceptions of the Course module, so every entry point reports the same code and wording. */
public final class CourseErrors {
    private CourseErrors() {}

    /** Missing or not visible: both answer the same 404, never 403. */
    public static NotFoundException notFound(String code) {
        return new NotFoundException(CourseErrorCode.COURSE_NOT_FOUND, "Course " + code + " not found");
    }
}
