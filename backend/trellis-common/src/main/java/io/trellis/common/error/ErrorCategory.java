package io.trellis.common.error;

/**
 * Coarse class of an error. The category, never the exception class, decides the HTTP status, so a module
 * adds error codes without touching the web layer.
 */
public enum ErrorCategory {
    VALIDATION(400, ProblemTypes.VALIDATION),
    UNAUTHENTICATED(401, ProblemTypes.UNAUTHENTICATED),
    FORBIDDEN(403, ProblemTypes.FORBIDDEN),
    NOT_FOUND(404, ProblemTypes.NOT_FOUND),
    METHOD_NOT_ALLOWED(405, ProblemTypes.BAD_REQUEST),
    NOT_ACCEPTABLE(406, ProblemTypes.BAD_REQUEST),
    CONFLICT(409, ProblemTypes.CONFLICT),
    PRECONDITION_FAILED(412, ProblemTypes.CONFLICT),
    PAYLOAD_TOO_LARGE(413, ProblemTypes.BAD_REQUEST),
    UNSUPPORTED_MEDIA_TYPE(415, ProblemTypes.BAD_REQUEST),
    RATE_LIMITED(429, ProblemTypes.RATE_LIMITED),
    INTERNAL(500, ProblemTypes.INTERNAL),
    UNAVAILABLE(503, ProblemTypes.UNAVAILABLE);

    private final int httpStatus;
    private final String problemType;

    ErrorCategory(int httpStatus, String problemType) {
        this.httpStatus = httpStatus;
        this.problemType = problemType;
    }

    /** RFC 7807 {@code type} URI of this category, one of {@link ProblemTypes}. */
    public String problemType() {
        return problemType;
    }

    public int httpStatus() {
        return httpStatus;
    }

    /** Server-side failures: their detail is never shown to the client. */
    public boolean isServerError() {
        return httpStatus >= 500;
    }
}
