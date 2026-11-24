package io.trellis.notice.api.dto;

/**
 * Who may read a published notice. Same values as {@code
 * contracts/events/notice.published/v1.schema.json}.
 */
public enum NoticeAudience {
  ALL,
  STAFF
}
