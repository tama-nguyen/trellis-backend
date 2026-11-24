package io.trellis.platform.events.consumer;

/**
 * What happens when a handler still fails after all attempts. Same names and meaning as the Python
 * worker.
 */
public enum FailureMode {
  /**
   * Write the event to the DLQ, then acknowledge and move on. Default: one poison event does not
   * stall a key.
   */
  CONTINUE,
  /**
   * Do not dead-letter and do not acknowledge: stop consuming (the Kafka container stops). The
   * event is redelivered after a restart. Use when skipping an event would corrupt the read model
   * (strict key order).
   */
  THROW
}
