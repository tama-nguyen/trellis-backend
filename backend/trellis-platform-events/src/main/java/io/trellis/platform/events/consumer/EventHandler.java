package io.trellis.platform.events.consumer;

import io.trellis.common.context.RequestContext;

/**
 * Consumes one event type. Implement as a Spring bean; the platform subscribes it (Kafka consumer group
 * {@code <groupPrefix><consumerName>}, or the in-memory broker), deduplicates through the inbox, retries and
 * dead-letters. {@link #handle} runs inside a transaction that also holds the inbox row, so database side effects
 * happen exactly once. Side effects outside the database (mail, HTTP) must be idempotent on {@code eventId}.
 *
 * @param <T> payload type, deserialised from {@code contracts/events/<eventType>/v<n>.schema.json}
 */
public interface EventHandler<T> {

    /** Unique, stable name; the inbox key and consumer-group suffix. Renaming it replays the whole topic. */
    String consumerName();

    String eventType();

    /** Highest payload major version this handler understands; newer events are not acked but dead-lettered. */
    default int maxEventVersion() {
        return 1;
    }

    Class<T> payloadType();

    /** Switch off a handler by configuration without removing it. */
    default boolean isEnabled() {
        return true;
    }

    /** Order among handlers of the same event type in one process (lower first). */
    default int order() {
        return 0;
    }

    default FailureMode failureMode() {
        return FailureMode.CONTINUE;
    }

    void handle(RequestContext ctx, InboundEvent<T> event);
}
