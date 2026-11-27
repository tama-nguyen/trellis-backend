package io.trellis.notification.api.dto;

/** Lifecycle: DRAFT -> PUBLISHED -> ARCHIVED, and ARCHIVED -> PUBLISHED again (re-publish). */
public enum NotificationStatus {
  DRAFT,
  PUBLISHED,
  ARCHIVED
}
