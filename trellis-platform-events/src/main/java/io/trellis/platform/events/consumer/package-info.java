/**
 * At-least-once consumers: {@link io.trellis.platform.events.consumer.EventHandler} beans, an inbox row per
 * (consumer, event) in the handler's transaction, retry with the shared {@code Backoff}, then the handler's
 * {@link io.trellis.platform.events.consumer.FailureMode}: dead-letter and continue, or stop. Same semantics as
 * the Python worker.
 */
package io.trellis.platform.events.consumer;
