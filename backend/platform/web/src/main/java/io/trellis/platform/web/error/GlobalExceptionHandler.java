package io.trellis.platform.web.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The single exception handler of the application. Catches everything thrown below a controller (including
 * Spring MVC's own exceptions, which reach the advice before the default resolver) and answers with an RFC
 * 7807 body. Never add a second {@code @ControllerAdvice}; add a {@link ProblemMapper} instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ProblemFactory problems;

    public GlobalExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handle(Throwable error, HttpServletRequest request) {
        MappedProblem mapped = problems.resolve(error);
        ProblemDetail body = problems.create(mapped, request, error);
        return ResponseEntity.status(body.getStatus()).headers(mapped.headers()).body(body);
    }
}
