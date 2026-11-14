package io.trellis.common.error;

/**
 * Field names of the RFC 7807 problem body. The standard members plus the Trellis extensions; the web
 * BFF and Python clients parse exactly these names.
 */
public final class ProblemFields {
    public static final String TYPE = "type";
    public static final String TITLE = "title";
    public static final String STATUS = "status";
    public static final String DETAIL = "detail";
    public static final String INSTANCE = "instance";
    /** Extension: stable {@link ErrorCode#code()}. */
    public static final String CODE = "code";
    /** Extension: request correlation id, also in the X-Correlation-Id header. */
    public static final String CORRELATION_ID = "correlationId";
    /** Extension: OpenTelemetry trace id when tracing is active. */
    public static final String TRACE_ID = "traceId";
    /** Extension: list of {field, code, message} for validation errors (name agreed with web/). */
    public static final String ERRORS = "errors";

    private ProblemFields() {}
}
