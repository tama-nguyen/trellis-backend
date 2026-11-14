package io.trellis.platform.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.trellis.common.context.TraceContextAccessor;
import java.util.Optional;

/** {@link TraceContextAccessor} over Micrometer Tracing (OpenTelemetry bridge). */
final class MicrometerTraceContextAccessor implements TraceContextAccessor {
    private static final String TRACEPARENT_VERSION = "00";
    private static final String SAMPLED = "01";
    private static final String NOT_SAMPLED = "00";

    private final Tracer tracer;

    MicrometerTraceContextAccessor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Optional<String> traceId() {
        return context().map(TraceContext::traceId);
    }

    @Override
    public Optional<String> traceparent() {
        return context()
                .map(ctx -> String.join(
                        "-",
                        TRACEPARENT_VERSION,
                        ctx.traceId(),
                        ctx.spanId(),
                        Boolean.TRUE.equals(ctx.sampled()) ? SAMPLED : NOT_SAMPLED));
    }

    private Optional<TraceContext> context() {
        Span span = tracer.currentSpan();
        if (span == null || span.isNoop()) {
            return Optional.empty();
        }
        return Optional.of(span.context());
    }
}
