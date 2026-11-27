package io.trellis.notification.application.view;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Read model fed by {@code notification.published}: how often a notification was published and
 * when. All zero/null for a notification never published (or whose event the projection has not
 * seen yet: it is eventually consistent).
 */
public record NotificationStatsView(
    @NotNull String notificationCode,
    @NotNull int publishCount,
    Instant firstPublishedAt,
    Instant lastPublishedAt) {
  public static NotificationStatsView empty(String notificationCode) {
    return new NotificationStatsView(notificationCode, 0, null, null);
  }
}
