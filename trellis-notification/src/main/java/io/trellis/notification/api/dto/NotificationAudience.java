package io.trellis.notification.api.dto;

/**
 * Who may read a published notification. Same values as {@code
 * contracts/events/notification.published/v1.schema.json}.
 */
public enum NotificationAudience {
  ALL,
  STAFF
}
