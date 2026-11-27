package io.trellis.notification.application.view;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.api.dto.NotificationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A notification as clients see it (REST detail, GraphQL {@code Notification}). {@code version} is
 * also the ETag. {@code @NotNull} marks the fields that are always present: the OpenAPI document
 * (and the clients generated from it) declares them required; the others may be null.
 *
 * @param code public identifier, {@code NT-XXXX-XXXX-XXXX}
 * @param publishedAt last publication time; null while a draft
 * @param archivedAt null unless archived
 */
public record NotificationView(
    @NotNull String code,
    @NotNull String title,
    @NotNull String body,
    @NotNull NotificationSeverity severity,
    @NotNull NotificationAudience audience,
    @NotNull NotificationStatus status,
    @NotNull long version,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt,
    Instant publishedAt,
    Instant archivedAt) {}
