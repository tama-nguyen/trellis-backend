package io.trellis.platform.observability;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.trellis.common.context.AmbientRequestContext;
import io.trellis.common.context.RequestContext;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the caller's context to another thread: the Micrometer context snapshot (current observation and
 * trace span), the MDC map, and the ambient {@link RequestContext}. Restores the worker thread afterwards so
 * pooled threads never leak one request's context into the next.
 */
public final class ContextPropagation implements TaskDecorator {
    private final ContextSnapshotFactory snapshots =
            ContextSnapshotFactory.builder().build();

    @Override
    public Runnable decorate(Runnable task) {
        ContextSnapshot snapshot = snapshots.captureAll();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Optional<RequestContext> requestContext = AmbientRequestContext.current();
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try (ContextSnapshot.Scope traceScope = snapshot.setThreadLocals();
                    AmbientRequestContext.Scope ctxScope =
                            requestContext.map(AmbientRequestContext::open).orElse(() -> {})) {
                setMdc(mdc);
                task.run();
            } finally {
                setMdc(previousMdc);
            }
        };
    }

    /** Wraps any executor so tasks submitted to it keep the caller's context. */
    public Executor wrap(Executor delegate) {
        return task -> delegate.execute(decorate(task));
    }

    private static void setMdc(Map<String, String> values) {
        if (values == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(values);
        }
    }
}
