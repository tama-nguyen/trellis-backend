package io.trellis.course.web.rest;

import io.trellis.course.domain.model.CourseDetails;
import jakarta.validation.constraints.NotNull;

/**
 * Body of create and update. Bean Validation checks shape only (present, right type); the business limits are
 * {@code CourseValidator}'s, so GraphQL or a consumer calling the service get the same rules.
 */
record CourseDetailsRequest(@NotNull String name, @NotNull String description) {
    CourseDetails toDetails() {
        return new CourseDetails(name, description);
    }
}
