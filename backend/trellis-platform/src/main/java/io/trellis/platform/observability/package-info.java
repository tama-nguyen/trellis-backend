/**
 * Observability platform: Micrometer + Prometheus metrics, OpenTelemetry tracing through the Micrometer
 * bridge, structured JSON logging fields, and executors that carry MDC, {@code RequestContext} and the trace
 * span to worker threads.
 *
 * <p>Rule: never call {@code CompletableFuture.runAsync/supplyAsync} without an executor; the common pool
 * drops all context and is shared with the JVM. Use the {@code applicationTaskExecutor} bean (already
 * decorated) or wrap your own with {@link io.trellis.platform.observability.ContextPropagation}. ArchUnit
 * enforces the ban.
 */
package io.trellis.platform.observability;
