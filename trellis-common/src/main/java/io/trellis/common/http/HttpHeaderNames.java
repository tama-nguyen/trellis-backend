package io.trellis.common.http;

/** HTTP header names used by Trellis. Python workers and the web BFF use the same spelling. */
public final class HttpHeaderNames {
  /** Request correlation id; echoed on every response and copied into outbox events. */
  public static final String CORRELATION_ID = "X-Correlation-Id";

  /** Client-chosen key that makes a write safe to retry. */
  public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  /** Seconds the client should wait before retrying a 429 or 503. */
  public static final String RETRY_AFTER = "Retry-After";

  /** W3C Trace Context parent header. */
  public static final String TRACEPARENT = "traceparent";

  private HttpHeaderNames() {}
}
