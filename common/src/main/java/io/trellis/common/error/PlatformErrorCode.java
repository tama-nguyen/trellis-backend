package io.trellis.common.error;

/** Error codes owned by the platform itself. Business modules define their own enum. */
public enum PlatformErrorCode implements ErrorCode {
    VALIDATION_FAILED(ErrorCategory.VALIDATION, "Request validation failed"),
    MALFORMED_REQUEST(ErrorCategory.VALIDATION, "Request body could not be read"),
    TYPE_MISMATCH(ErrorCategory.VALIDATION, "A parameter has the wrong type"),
    MISSING_PARAMETER(ErrorCategory.VALIDATION, "A required parameter is missing"),
    INVALID_CURSOR(ErrorCategory.VALIDATION, "The page cursor is invalid"),
    UNAUTHENTICATED(ErrorCategory.UNAUTHENTICATED, "Authentication required"),
    ACCESS_DENIED(ErrorCategory.FORBIDDEN, "Access denied"),
    ROUTE_NOT_FOUND(ErrorCategory.NOT_FOUND, "No such endpoint"),
    RESOURCE_NOT_FOUND(ErrorCategory.NOT_FOUND, "Resource not found"),
    FEATURE_DISABLED(ErrorCategory.NOT_FOUND, "Feature is not available"),
    METHOD_NOT_ALLOWED(ErrorCategory.METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    NOT_ACCEPTABLE(ErrorCategory.NOT_ACCEPTABLE, "Requested media type is not available"),
    CONCURRENT_MODIFICATION(ErrorCategory.CONFLICT, "The resource was changed by someone else"),
    IDEMPOTENCY_IN_PROGRESS(ErrorCategory.CONFLICT, "A request with this idempotency key is still running"),
    LOCK_NOT_ACQUIRED(ErrorCategory.CONFLICT, "The resource is busy"),
    PRECONDITION_FAILED(ErrorCategory.PRECONDITION_FAILED, "Precondition failed"),
    PAYLOAD_TOO_LARGE(ErrorCategory.PAYLOAD_TOO_LARGE, "Payload too large"),
    UNSUPPORTED_MEDIA_TYPE(ErrorCategory.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    RATE_LIMITED(ErrorCategory.RATE_LIMITED, "Too many requests"),
    SERVICE_UNAVAILABLE(ErrorCategory.UNAVAILABLE, "Service temporarily unavailable"),
    INTERNAL_ERROR(ErrorCategory.INTERNAL, "Unexpected error");

    private final ErrorCategory category;
    private final String title;

    PlatformErrorCode(ErrorCategory category, String title) {
        this.category = category;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String title() {
        return title;
    }
}
