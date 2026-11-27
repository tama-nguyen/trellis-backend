package io.trellis.notification.api.event;

import io.trellis.notification.api.dto.NotificationAudience;
import java.time.Instant;

/**
 * Payload of {@code notification.published} v1. Must match {@code
 * contracts/events/notification.published/v1.schema.json} field for field; change the schema first,
 * and make a breaking change a new version.
 */
public record NotificationPublishedV1(
    String notificationCode,
    String title,
    NotificationAudience audience,
    Instant publishedAt,
    String publishedBy) {
  public static final String EVENT_TYPE = "notification.published";
  public static final int EVENT_VERSION = 1;
}
