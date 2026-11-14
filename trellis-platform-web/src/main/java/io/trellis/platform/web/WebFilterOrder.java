package io.trellis.platform.web;

import org.springframework.core.Ordered;

/**
 * Servlet filter order of the platform. Spring's {@code ServerHttpObservationFilter} runs at {@code
 * HIGHEST_PRECEDENCE + 1} and opens the server span, so correlation runs after it to see the trace id. Spring
 * Security's chain runs at -100, so the request-context filter, which needs the authenticated actor, runs after.
 */
public final class WebFilterOrder {
    public static final int CORRELATION_ID = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int REQUEST_LOG = Ordered.HIGHEST_PRECEDENCE + 20;
    public static final int REQUEST_CONTEXT = -90;

    private WebFilterOrder() {}
}
