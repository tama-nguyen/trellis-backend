/**
 * Event platform (decision T11): transactional outbox ({@link io.trellis.platform.events.outbox.OutboxWriter},
 * MANDATORY propagation), an ordered relay (head-of-key, per-shard advisory locks, broker-outage defer), the
 * {@link io.trellis.platform.events.transport.EventTransport} SPI ({@code kafka} | {@code inmemory}), and an
 * at-least-once consumer framework with inbox, retry with backoff, replayable DLQ and per-event MDC.
 *
 * <p>Wire format: {@code contracts/events/envelope.schema.json}. Python workers implement the same envelope,
 * headers, inbox table and DLQ conventions.
 */
package io.trellis.platform.events;
