package io.trellis.common.context;

import java.util.Optional;

/**
 * Read access to the current trace, implemented by the observability module on top of Micrometer
 * Tracing. Lets the web and events modules stamp trace ids without depending on a tracing library.
 */
public interface TraceContextAccessor {
  TraceContextAccessor NOOP =
      new TraceContextAccessor() {
        @Override
        public Optional<String> traceId() {
          return Optional.empty();
        }

        @Override
        public Optional<String> traceparent() {
          return Optional.empty();
        }
      };

  /** 32 hex chars, when a span is active. */
  Optional<String> traceId();

  /** W3C {@code traceparent} of the current span, for outbound events. */
  Optional<String> traceparent();
}
