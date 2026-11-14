package io.trellis.platform.events.outbox;

import java.util.Objects;

/**
 * An event to publish. {@code payload} must serialise to JSON matching {@code
 * contracts/events/<eventType>/v<eventVersion>.schema.json}.
 *
 * @param partitionKey ordering key; events with the same key reach consumers in write order
 */
public record OutboxMessage(
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        String partitionKey,
        Object payload) {

    public OutboxMessage {
        requireText(eventType, "eventType");
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        requireText(partitionKey, "partitionKey");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion starts at 1");
        }
        Objects.requireNonNull(payload, "payload");
    }

    /** Version 1, ordered by its own aggregate: the common case. */
    public static OutboxMessage of(String eventType, String aggregateType, String aggregateId, Object payload) {
        return new OutboxMessage(eventType, 1, aggregateType, aggregateId, aggregateType + ":" + aggregateId, payload);
    }

    public OutboxMessage withVersion(int version) {
        return new OutboxMessage(eventType, version, aggregateType, aggregateId, partitionKey, payload);
    }

    /** Ordered with another aggregate's events, e.g. a child event keyed by its parent. */
    public OutboxMessage orderedBy(String key) {
        return new OutboxMessage(eventType, eventVersion, aggregateType, aggregateId, key, payload);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
