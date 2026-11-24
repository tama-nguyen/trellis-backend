package io.trellis.identity.api.error;

import io.trellis.common.error.ErrorCategory;
import io.trellis.common.error.ErrorCode;

/** Stable wire codes for native user and session failures. */
public enum IdentityErrorCode implements ErrorCode {
  INVALID_CREDENTIALS(ErrorCategory.UNAUTHENTICATED, "Invalid credentials"),
  EMAIL_NOT_VERIFIED(ErrorCategory.FORBIDDEN, "Email verification required"),
  REFRESH_TOKEN_INVALID(ErrorCategory.UNAUTHENTICATED, "Refresh token invalid"),
  ACTION_TOKEN_INVALID(ErrorCategory.VALIDATION, "Action token invalid or expired"),
  USER_ALREADY_EXISTS(ErrorCategory.CONFLICT, "User already exists"),
  USER_DISABLED(ErrorCategory.FORBIDDEN, "User disabled");

  private final ErrorCategory category;
  private final String title;

  IdentityErrorCode(ErrorCategory category, String title) {
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
