package io.trellis.notice.api.dto;

/** Lifecycle: DRAFT -> PUBLISHED -> ARCHIVED, and ARCHIVED -> PUBLISHED again (re-publish). */
public enum NoticeStatus {
  DRAFT,
  PUBLISHED,
  ARCHIVED
}
