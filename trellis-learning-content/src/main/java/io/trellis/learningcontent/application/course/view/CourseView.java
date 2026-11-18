package io.trellis.learningcontent.application.course.view;

import io.trellis.learningcontent.api.dto.CourseStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A course as clients see it (REST detail, GraphQL {@code Course}). {@code version} is also the ETag. {@code
 * @NotNull} marks the fields that are always present: the OpenAPI document (and the clients generated from it)
 * declares them required; the others may be null.
 *
 * @param code public identifier, {@code CO-XXXX-XXXX-XXXX}
 * @param activatedAt first activation, null while a draft
 * @param archivedAt null unless archived
 */
public record CourseView(
        @NotNull String code,
        @NotNull String name,
        @NotNull String description,
        @NotNull CourseStatus status,
        @NotNull long version,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        Instant activatedAt,
        Instant archivedAt) {}
