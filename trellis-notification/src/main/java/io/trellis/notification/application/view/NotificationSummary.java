package io.trellis.notification.application.view;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.api.dto.NotificationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * List row: a notification without its body. {@code @NotNull} fields are required in the OpenAPI
 * document.
 */
public record NotificationSummary(
    @NotNull String code,
    @NotNull String title,
    @NotNull NotificationSeverity severity,
    @NotNull NotificationAudience audience,
    @NotNull NotificationStatus status,
    @NotNull Instant createdAt,
    Instant publishedAt) {}
