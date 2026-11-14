package io.trellis.platform.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Event platform settings.
 *
 * @param transport {@code inmemory} (single process, dev/test) or {@code kafka}
 * @param producer value of the envelope {@code producer} field
 * @param topicPrefix prepended to the event type to form the topic (e.g. {@code prod.}); empty by default
 * @param relay outbox relay
 * @param consumer consumer framework
 * @param retention cleanup of published outbox rows and inbox rows
 */
@ConfigurationProperties(prefix = "trellis.events")
public record EventsProperties(
        @DefaultValue(TRANSPORT_INMEMORY) String transport,
        @DefaultValue("trellis-backend") String producer,
        @DefaultValue("") String topicPrefix,
        @DefaultValue Relay relay,
        @DefaultValue Consumer consumer,
        @DefaultValue Retention retention) {

    public static final String PREFIX = "trellis.events";
    public static final String TRANSPORT_KAFKA = "kafka";
    public static final String TRANSPORT_INMEMORY = "inmemory";

    /**
     * @param enabled run the relay in this process (turn off on instances that only serve reads)
     * @param shards number of ordering shards; change only while the outbox has no NEW rows
     * @param batchSize heads sent per pass and shard
     * @param pollInterval delay between relay ticks
     * @param maxPassesPerTick passes per shard per tick while rows keep flowing
     * @param maxAttempts row-caused failures before a row becomes FAILED (and blocks its key)
     * @param baseBackoff backoff of the first retry; also the defer delay when the broker is down
     * @param maxBackoff cap of the retry backoff
     * @param sendTimeout wait for broker acknowledgements per pass
     * @param metricsInterval refresh of the outbox gauges
     */
    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("4") int shards,
            @DefaultValue("100") int batchSize,
            @DefaultValue("PT0.5S") Duration pollInterval,
            @DefaultValue("10") int maxPassesPerTick,
            @DefaultValue("10") int maxAttempts,
            @DefaultValue("PT1S") Duration baseBackoff,
            @DefaultValue("PT5M") Duration maxBackoff,
            @DefaultValue("PT5S") Duration sendTimeout,
            @DefaultValue("PT15S") Duration metricsInterval) {}

    /**
     * @param groupPrefix Kafka consumer group = prefix + handler consumer name
     * @param maxAttempts total handler attempts (same default as the Python worker) before the failure mode applies
     * @param baseBackoff backoff of the first handler retry
     * @param maxBackoff cap of the handler retry backoff
     */
    public record Consumer(
            @DefaultValue("trellis.") String groupPrefix,
            @DefaultValue("4") int maxAttempts,
            @DefaultValue("PT1S") Duration baseBackoff,
            @DefaultValue("PT30S") Duration maxBackoff) {}

    /**
     * @param publishedFor keep PUBLISHED outbox rows this long (short-range replay and forensics)
     * @param processedFor keep inbox rows this long; must exceed the broker's topic retention
     * @param batchSize rows deleted per statement, to keep locks and WAL small
     * @param cron schedule of the cleanup
     */
    public record Retention(
            @DefaultValue("P7D") Duration publishedFor,
            @DefaultValue("P14D") Duration processedFor,
            @DefaultValue("5000") int batchSize,
            @DefaultValue("0 41 3 * * *") String cron) {}
}
