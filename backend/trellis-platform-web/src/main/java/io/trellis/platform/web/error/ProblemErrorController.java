package io.trellis.platform.web.error;

import io.trellis.common.error.PlatformErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code /error} page so failures outside Spring MVC (a filter throwing, the container
 * rejecting a request) still answer with the same RFC 7807 body instead of Boot's own JSON shape.
 */
@RestController
public class ProblemErrorController implements ErrorController {
    static final String PATH = "/error";

    private final ProblemFactory problems;

    public ProblemErrorController(ProblemFactory problems) {
        this.problems = problems;
    }

    @RequestMapping(PATH)
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        Throwable error = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        MappedProblem mapped = error != null ? problems.resolve(error) : MappedProblem.of(byStatus(request), null);
        ProblemDetail body = problems.create(mapped, request, error);
        return ResponseEntity.status(body.getStatus()).headers(mapped.headers()).body(body);
    }

    private static PlatformErrorCode byStatus(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = raw instanceof Integer code ? HttpStatus.resolve(code) : null;
        if (status == null) {
            return PlatformErrorCode.INTERNAL_ERROR;
        }
        return switch (status) {
            case NOT_FOUND -> PlatformErrorCode.ROUTE_NOT_FOUND;
            case BAD_REQUEST -> PlatformErrorCode.MALFORMED_REQUEST;
            case UNAUTHORIZED -> PlatformErrorCode.UNAUTHENTICATED;
            case FORBIDDEN -> PlatformErrorCode.ACCESS_DENIED;
            case METHOD_NOT_ALLOWED -> PlatformErrorCode.METHOD_NOT_ALLOWED;
            case PAYLOAD_TOO_LARGE -> PlatformErrorCode.PAYLOAD_TOO_LARGE;
            case SERVICE_UNAVAILABLE -> PlatformErrorCode.SERVICE_UNAVAILABLE;
            default -> PlatformErrorCode.INTERNAL_ERROR;
        };
    }
}
