package io.trellis.platform.web.error;

import io.trellis.common.error.ErrorCode;
import io.trellis.common.error.PlatformErrorCode;
import io.trellis.common.error.ProblemFields;
import io.trellis.common.validation.Violation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps the Spring MVC, validation and data-access exceptions that reach a controller boundary. Details are
 * rewritten in our own words: framework messages can quote class names, JSON fragments or SQL.
 */
public final class FrameworkProblemMapper implements ProblemMapper {
    private static final String INVALID_VALUE = "INVALID";

    @Override
    public Optional<MappedProblem> map(Throwable error) {
        MappedProblem problem = switch (error) {
            case BindException e ->
                validation(e.getFieldErrors().stream()
                        .map(FrameworkProblemMapper::toViolation)
                        .toList());
            case HandlerMethodValidationException e ->
                validation(e.getParameterValidationResults().stream()
                        .flatMap(r -> r.getResolvableErrors().stream()
                                .map(err -> new Violation(
                                        r.getMethodParameter().getParameterName(),
                                        code(err.getCodes()),
                                        String.valueOf(err.getDefaultMessage()))))
                        .toList());
            case ConstraintViolationException e ->
                validation(e.getConstraintViolations().stream()
                        .map(v -> new Violation(String.valueOf(v.getPropertyPath()), INVALID_VALUE, v.getMessage()))
                        .toList());
            case MethodArgumentTypeMismatchException e ->
                MappedProblem.of(PlatformErrorCode.TYPE_MISMATCH, "Parameter '" + e.getName() + "' has the wrong type");
            case HttpMessageNotReadableException e ->
                MappedProblem.of(PlatformErrorCode.MALFORMED_REQUEST, "The request body is missing or not valid JSON");
            case HttpRequestMethodNotSupportedException e -> methodNotAllowed(e);
            case HttpMediaTypeNotSupportedException e ->
                MappedProblem.of(PlatformErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported");
            case HttpMediaTypeNotAcceptableException e ->
                MappedProblem.of(PlatformErrorCode.NOT_ACCEPTABLE, "None of the accepted media types can be produced");
            case MissingServletRequestPartException e ->
                MappedProblem.of(
                        PlatformErrorCode.MISSING_PARAMETER,
                        "Required part '" + e.getRequestPartName() + "' is missing");
            case MissingRequestValueException e ->
                MappedProblem.of(PlatformErrorCode.MISSING_PARAMETER, "A required request value is missing");
            case ServletRequestBindingException e ->
                MappedProblem.of(PlatformErrorCode.MISSING_PARAMETER, "The request is missing required values");
            case MaxUploadSizeExceededException e ->
                MappedProblem.of(PlatformErrorCode.PAYLOAD_TOO_LARGE, "The upload exceeds the size limit");
            case NoResourceFoundException e ->
                MappedProblem.of(PlatformErrorCode.ROUTE_NOT_FOUND, "No endpoint matches this path");
            case NoHandlerFoundException e ->
                MappedProblem.of(PlatformErrorCode.ROUTE_NOT_FOUND, "No endpoint matches this path");
            case OptimisticLockingFailureException e ->
                MappedProblem.of(
                        PlatformErrorCode.CONCURRENT_MODIFICATION,
                        "The resource was changed by someone else; reload and retry");
            case AsyncRequestTimeoutException e ->
                MappedProblem.of(PlatformErrorCode.SERVICE_UNAVAILABLE, "The request timed out");
            case ErrorResponse e -> MappedProblem.of(byStatus(e.getStatusCode()), null);
            default -> null;
        };
        return Optional.ofNullable(problem);
    }

    private static MappedProblem validation(List<Violation> violations) {
        return new MappedProblem(
                PlatformErrorCode.VALIDATION_FAILED,
                violations.size() + " validation rule(s) failed",
                Map.of(ProblemFields.ERRORS, violations),
                new HttpHeaders());
    }

    private static MappedProblem methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        HttpHeaders headers = new HttpHeaders();
        if (e.getSupportedHttpMethods() != null) {
            headers.setAllow(e.getSupportedHttpMethods());
        }
        return new MappedProblem(
                PlatformErrorCode.METHOD_NOT_ALLOWED,
                "Method " + e.getMethod() + " is not supported here",
                Map.of(),
                headers);
    }

    private static Violation toViolation(FieldError error) {
        return new Violation(error.getField(), code(error.getCodes()), String.valueOf(error.getDefaultMessage()));
    }

    /** Bean Validation codes look like {@code NotBlank.cmd.title}; the last, least specific one is the constraint. */
    private static String code(String[] codes) {
        if (codes == null || codes.length == 0) {
            return INVALID_VALUE;
        }
        String constraint = codes[codes.length - 1];
        return constraint.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
    }

    private static ErrorCode byStatus(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            return PlatformErrorCode.INTERNAL_ERROR;
        }
        return switch (resolved) {
            case BAD_REQUEST -> PlatformErrorCode.MALFORMED_REQUEST;
            case UNAUTHORIZED -> PlatformErrorCode.UNAUTHENTICATED;
            case FORBIDDEN -> PlatformErrorCode.ACCESS_DENIED;
            case NOT_FOUND -> PlatformErrorCode.ROUTE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> PlatformErrorCode.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> PlatformErrorCode.NOT_ACCEPTABLE;
            case CONFLICT -> PlatformErrorCode.CONCURRENT_MODIFICATION;
            case PRECONDITION_FAILED -> PlatformErrorCode.PRECONDITION_FAILED;
            case PAYLOAD_TOO_LARGE -> PlatformErrorCode.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> PlatformErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case TOO_MANY_REQUESTS -> PlatformErrorCode.RATE_LIMITED;
            case SERVICE_UNAVAILABLE -> PlatformErrorCode.SERVICE_UNAVAILABLE;
            default -> PlatformErrorCode.INTERNAL_ERROR;
        };
    }
}
