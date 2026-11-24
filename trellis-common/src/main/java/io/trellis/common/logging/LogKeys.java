package io.trellis.common.logging;

/**
 * MDC keys written by the platform. Snake case because they become top-level fields of the JSON log
 * line, and Python workers log the same names via structlog.
 */
public final class LogKeys {
  public static final String CORRELATION_ID = "correlation_id";
  public static final String TRACE_ID = "trace_id";
  public static final String SPAN_ID = "span_id";
  public static final String ACTOR = "actor";
  public static final String ACTOR_TYPE = "actor_type";
  public static final String TENANT = "tenant";
  public static final String EVENT_ID = "event_id";
  public static final String EVENT_TYPE = "event_type";
  public static final String CONSUMER = "consumer";
  public static final String TOPIC = "topic";

  private LogKeys() {}
}
