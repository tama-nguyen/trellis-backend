package io.trellis.learningcontent.application.course.view;

import io.trellis.learningcontent.api.dto.CourseStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** List row: a course without its long fields. {@code @NotNull} fields are required in the OpenAPI document. */
public record CourseSummary(
        @NotNull String code,
        @NotNull String name,
        @NotNull CourseStatus status,
        @NotNull Instant createdAt,
        Instant activatedAt) {}
