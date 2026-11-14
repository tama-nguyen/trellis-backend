package io.trellis.platform.events.consumer;

import java.util.UUID;

/** Deduplicates deliveries. Must run inside the transaction that applies the side effect. */
public interface InboxGuard {
    /** True on the first delivery of {@code eventId} to {@code consumerName}; false for a duplicate. */
    boolean firstDelivery(String consumerName, UUID eventId, String correlationId);
}
