package io.trellis.notification.domain.model;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;

/**
 * The editable part of a notification; the command of both create and update. Checked by the
 * application's validation pipeline, not here, so every violation is reported at once.
 */
public record NotificationContent(
    String title, String body, NotificationSeverity severity, NotificationAudience audience) {
  /**
   * Limits shared with the database columns and the event contract ({@code title} maxLength 200).
   */
  public static final int TITLE_MAX = 200;

  public static final int BODY_MAX = 20_000;
}
