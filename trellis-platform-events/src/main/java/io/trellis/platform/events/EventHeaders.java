package io.trellis.platform.events;

/**
 * Message header names. Readable without parsing the body (DLQ tooling, Kafka UI); the Python
 * worker uses the same names.
 */
public final class EventHeaders {
  public static final String EVENT_ID = "x-event-id";
  public static final String EVENT_TYPE = "x-event-type";
  public static final String EVENT_VERSION = "x-event-version";
  public static final String CORRELATION_ID = "x-correlation-id";
  public static final String TRACEPARENT = "traceparent";

  public static final String DLQ_ORIGINAL_TOPIC = "x-dlq-original-topic";
  public static final String DLQ_ORIGINAL_PARTITION = "x-dlq-original-partition";
  public static final String DLQ_ORIGINAL_OFFSET = "x-dlq-original-offset";
  public static final String DLQ_CONSUMER = "x-dlq-consumer";

  /** Exception class name (simple name). */
  public static final String DLQ_EXCEPTION = "x-dlq-exception";

  /** Exception message, truncated. */
  public static final String DLQ_ERROR = "x-dlq-error";

  public static final String DLQ_ATTEMPTS = "x-dlq-attempts";
  public static final String DLQ_FAILED_AT = "x-dlq-failed-at";

  /**
   * Suffix of the dead-letter topic: {@code <eventType topic>.dlq}, or {@code <source topic>.dlq}
   * when the body cannot be parsed into an envelope.
   */
  public static final String DLQ_SUFFIX = ".dlq";

  private EventHeaders() {}
}
