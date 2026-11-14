package io.trellis.platform.web.context;

import io.trellis.common.context.RequestContext;
import io.trellis.common.context.TraceContextAccessor;
import io.trellis.platform.security.ActorResolver;
import io.trellis.platform.web.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/** Builds the {@link RequestContext} of an HTTP request once and caches it as a request attribute. */
public final class RequestContextFactory {
    static final String ATTRIBUTE = RequestContextFactory.class.getName() + ".CONTEXT";

    private final ActorResolver actors;
    private final TraceContextAccessor trace;
    private final Clock clock;

    public RequestContextFactory(ActorResolver actors, TraceContextAccessor trace, Clock clock) {
        this.actors = actors;
        this.trace = trace;
        this.clock = clock;
    }

    /**
     * The context already built for this request, looked up in request attributes. For edges that see the
     * attributes but not the servlet request itself (GraphQL interceptors).
     */
    public static Optional<RequestContext> cached(Map<String, Object> requestAttributes) {
        return requestAttributes.get(ATTRIBUTE) instanceof RequestContext ctx ? Optional.of(ctx) : Optional.empty();
    }

    public RequestContext from(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof RequestContext cached) {
            return cached;
        }
        RequestContext ctx = RequestContext.builder()
                .actor(actors.currentActor())
                .correlationId(CorrelationIdFilter.correlationId(request))
                .traceId(trace.traceId().orElse(null))
                .locale(request.getLocale())
                .requestTime(clock.instant())
                .build();
        request.setAttribute(ATTRIBUTE, ctx);
        return ctx;
    }
}
