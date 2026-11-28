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

  /** Principal selected by a trusted server-to-server caller. */
  public static final String USER_ID = "X-User-Id";

  /** Credential presented by a trusted server-to-server caller. */
  public static final String INTERNAL_KEY = "X-Internal-Key";

  /** Policy restricting browser features for an HTTP response. */
  public static final String PERMISSIONS_POLICY = "Permissions-Policy";

  private HttpHeaderNames() {}
}
