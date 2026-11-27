package io.trellis.notification.api.error;

import io.trellis.common.error.NotFoundException;

/**
 * Exceptions of the Notification module, so every entry point reports the same code and wording.
 */
public final class NotificationErrors {
  private NotificationErrors() {}

  /** Missing or not visible: both answer the same 404, never 403. */
  public static NotFoundException notFound(String code) {
    return new NotFoundException(
        NotificationErrorCode.NOTIFICATION_NOT_FOUND, "Notification " + code + " not found");
  }
}
