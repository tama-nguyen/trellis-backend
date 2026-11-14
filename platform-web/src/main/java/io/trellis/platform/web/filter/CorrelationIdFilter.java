package io.trellis.platform.web.filter;

import io.trellis.common.http.HttpHeaderNames;
import io.trellis.common.id.UuidV7;
import io.trellis.common.logging.LogKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts a well-formed inbound {@code X-Correlation-Id} or mints a UUIDv7, then exposes it in the MDC, as a
 * request attribute and on the response. Malformed ids are replaced, not rejected: a client bug must not
 * break the call, and a hostile value must not reach the logs.
 */
public final class CorrelationIdFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".ID";

    private final Pattern accepted;
    private final Clock clock;

    public CorrelationIdFilter(Pattern accepted, Clock clock) {
        this.accepted = accepted;
        this.clock = clock;
    }

    /** Correlation id of the request; mints one if the filter did not run (e.g. in a slice test). */
    public static String correlationId(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String id) {
            return id;
        }
        String id = UuidV7.next().toString();
        request.setAttribute(ATTRIBUTE, id);
        return id;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inbound = request.getHeader(HttpHeaderNames.CORRELATION_ID);
        String id = inbound != null && accepted.matcher(inbound).matches()
                ? inbound
                : UuidV7.next(clock).toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HttpHeaderNames.CORRELATION_ID, id);
        MDC.put(LogKeys.CORRELATION_ID, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogKeys.CORRELATION_ID);
        }
    }
}
