package io.trellis.platform.events.consumer;

/**
 * Thrown by a handler when retrying cannot help (the event itself is wrong); skips straight to the
 * failure mode.
 */
public class NonRetryableEventException extends RuntimeException {
  public NonRetryableEventException(String message, Throwable cause) {
    super(message, cause);
  }

  public NonRetryableEventException(String message) {
    super(message);
  }
}
