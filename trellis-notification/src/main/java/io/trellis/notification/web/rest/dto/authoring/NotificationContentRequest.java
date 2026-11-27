package io.trellis.notification.web.rest.dto.authoring;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.domain.model.NotificationContent;
import jakarta.validation.constraints.NotNull;

/**
 * Body of create and update. Bean Validation checks shape only (present, right type); the business
 * limits are {@code NotificationContentValidator}'s, so GraphQL or a consumer calling the service
 * get the same rules.
 */
public record NotificationContentRequest(
    @NotNull String title,
    @NotNull String body,
    @NotNull NotificationSeverity severity,
    @NotNull NotificationAudience audience) {
  public NotificationContent toContent() {
    return new NotificationContent(title, body, severity, audience);
  }
}
