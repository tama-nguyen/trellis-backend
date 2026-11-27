package io.trellis.common.error;

/**
 * A stable, documented error code. Implement it with an enum per module ({@code
 * NotificationErrorCode}), never with ad-hoc strings. Codes are part of the API contract: clients
 * branch on them, so renaming one is breaking.
 */
public interface ErrorCode {
  /** UPPER_SNAKE_CASE, unique across the application, e.g. {@code NOTIFICATION_NOT_FOUND}. */
  String code();

  ErrorCategory category();

  /**
   * Short, human readable summary used as the problem {@code title}. Never contains request data.
   */
  String title();
}
