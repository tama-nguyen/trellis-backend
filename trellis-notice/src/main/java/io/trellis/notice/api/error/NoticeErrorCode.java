package io.trellis.notice.api.error;

import io.trellis.common.error.ErrorCategory;
import io.trellis.common.error.ErrorCode;

/** Error codes of the Notice module; part of the API contract (clients branch on them). */
public enum NoticeErrorCode implements ErrorCode {
  /**
   * Missing, or not visible to the caller (both answer the same, so existence is never confirmed).
   */
  NOTICE_NOT_FOUND(ErrorCategory.NOT_FOUND, "Notice not found"),
  /** The requested status change is not allowed from the current status. */
  NOTICE_INVALID_TRANSITION(ErrorCategory.CONFLICT, "Notice status does not allow this change"),
  /** If-Match does not name the current version: someone else changed the notice. */
  NOTICE_VERSION_MISMATCH(ErrorCategory.PRECONDITION_FAILED, "Notice was changed by someone else"),
  /** If-Match is not a version ETag of this API. */
  NOTICE_INVALID_PRECONDITION(
      ErrorCategory.VALIDATION, "If-Match must be an ETag returned by this API");

  private final ErrorCategory category;
  private final String title;

  NoticeErrorCode(ErrorCategory category, String title) {
    this.category = category;
    this.title = title;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public ErrorCategory category() {
    return category;
  }

  @Override
  public String title() {
    return title;
  }
}
