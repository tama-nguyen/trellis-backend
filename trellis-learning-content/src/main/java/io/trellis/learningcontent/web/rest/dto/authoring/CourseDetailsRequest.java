package io.trellis.learningcontent.web.rest.dto.authoring;

import io.trellis.learningcontent.domain.course.model.CourseDetails;
import jakarta.validation.constraints.NotNull;

/**
 * Body of create and update. Bean Validation checks shape only (present, right type); the business limits are
 * {@code CourseValidator}'s, so GraphQL or a consumer calling the service get the same rules.
 */
public record CourseDetailsRequest(
        @NotNull String name, @NotNull String description) {
    public CourseDetails toDetails() {
        return new CourseDetails(name, description);
    }
}
