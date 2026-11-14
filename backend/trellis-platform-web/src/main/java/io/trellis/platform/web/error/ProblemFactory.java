package io.trellis.platform.web.error;

import io.trellis.common.context.TraceContextAccessor;
import io.trellis.common.error.ErrorCode;
import io.trellis.common.error.PlatformErrorCode;
import io.trellis.common.error.ProblemFields;
import io.trellis.platform.web.filter.CorrelationIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Turns a throwable into a {@link ProblemDetail}: runs the mappers, hides server-side detail, adds {@code
 * code}, {@code correlationId}, {@code traceId}, and logs. Shared by the controller advice and by filters
 * (security entry point, rate limiting) that answer before a controller runs.
 */
public final class ProblemFactory {
    private static final Logger log = LoggerFactory.getLogger(ProblemFactory.class);
    /** Shown instead of the real detail for every 5xx. The correlation id is how support finds the cause. */
    static final String GENERIC_SERVER_ERROR =
            "An unexpected error occurred. Quote the correlation id when reporting it.";

    private final List<ProblemMapper> mappers;
    private final TraceContextAccessor trace;

    public ProblemFactory(List<ProblemMapper> mappers, TraceContextAccessor trace) {
        this.mappers = List.copyOf(mappers);
        this.trace = trace;
    }

    public MappedProblem resolve(Throwable error) {
        for (ProblemMapper mapper : mappers) {
            Optional<MappedProblem> mapped = mapper.map(error);
            if (mapped.isPresent()) {
                return mapped.get();
            }
        }
        return MappedProblem.of(PlatformErrorCode.INTERNAL_ERROR, null);
    }

    public ProblemDetail create(MappedProblem problem, HttpServletRequest request, Throwable error) {
        ErrorCode code = problem.errorCode();
        boolean serverError = code.category().isServerError();
        String correlationId = CorrelationIdFilter.correlationId(request);
        logError(code, error, serverError);

        ProblemDetail detail =
                ProblemDetail.forStatus(HttpStatusCode.valueOf(code.category().httpStatus()));
        detail.setType(URI.create(code.category().problemType()));
        detail.setTitle(code.title());
        detail.setDetail(serverError ? GENERIC_SERVER_ERROR : problem.detail());
        detail.setInstance(URI.create(originalUri(request)));
        detail.setProperty(ProblemFields.CODE, code.code());
        detail.setProperty(ProblemFields.CORRELATION_ID, correlationId);
        trace.traceId().ifPresent(id -> detail.setProperty(ProblemFields.TRACE_ID, id));
        if (!serverError) {
            problem.properties().forEach(detail::setProperty);
        }
        return detail;
    }

    /** On the container's /error dispatch, report the URI the client actually called. */
    private static String originalUri(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri
                ? uri
                : request.getRequestURI();
    }

    private static void logError(ErrorCode code, Throwable error, boolean serverError) {
        if (serverError) {
            log.atError().addKeyValue("error_code", code.code()).setCause(error).log("Request failed");
        } else {
            log.atDebug()
                    .addKeyValue("error_code", code.code())
                    .addKeyValue(
                            "exception", error == null ? null : error.getClass().getSimpleName())
                    .log("Request rejected");
        }
    }
}
