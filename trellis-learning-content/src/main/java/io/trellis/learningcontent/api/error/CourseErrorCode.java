package io.trellis.learningcontent.api.error;

import io.trellis.common.error.ErrorCategory;
import io.trellis.common.error.ErrorCode;

/** Error codes of the Course module; part of the API contract (clients branch on them). */
public enum CourseErrorCode implements ErrorCode {
  /**
   * Missing, or not visible to the caller (both answer the same, so existence is never confirmed).
   */
  COURSE_NOT_FOUND(ErrorCategory.NOT_FOUND, "Course not found"),
  /** The requested status change is not allowed from the current status. */
  COURSE_INVALID_TRANSITION(ErrorCategory.CONFLICT, "Course status does not allow this change"),
  /** If-Match does not name the current version: someone else changed the course. */
  COURSE_VERSION_MISMATCH(ErrorCategory.PRECONDITION_FAILED, "Course was changed by someone else"),
  /** If-Match is not a version ETag of this API. */
  COURSE_INVALID_PRECONDITION(
      ErrorCategory.VALIDATION, "If-Match must be an ETag returned by this API");

  private final ErrorCategory category;
  private final String title;

  CourseErrorCode(ErrorCategory category, String title) {
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
