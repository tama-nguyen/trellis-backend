package io.trellis.platform.events;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * Event platform settings.
 *
 * <p>transport: {@code inmemory} (single process, dev/test) or {@code kafka} producer: value of the
 * envelope {@code producer} field topicPrefix: prepended to the event type to form the topic (e.g.
 * {@code prod.}); empty by default relay: outbox relay consumer: consumer framework retention:
 * cleanup of published outbox rows and inbox rows
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
public final class EventsProperties {
  @Value("${trellis.events.transport:inmemory}")
  private String transport;

  @Value("${trellis.events.producer:trellis-backend}")
  private String producer;

  @Value("${trellis.events.topic-prefix:}")
  private String topicPrefix;

  @Value("${trellis.events.relay.enabled:true}")
  private boolean relayEnabled;

  @Value("${trellis.events.relay.shards:4}")
  private int relayShards;

  @Value("${trellis.events.relay.batch-size:100}")
  private int relayBatchSize;

  @Value("${trellis.events.relay.poll-interval:PT0.5S}")
  private Duration relayPollInterval;

  @Value("${trellis.events.relay.max-passes-per-tick:10}")
  private int relayMaxPassesPerTick;

  @Value("${trellis.events.relay.max-attempts:10}")
  private int relayMaxAttempts;

  @Value("${trellis.events.relay.base-backoff:PT1S}")
  private Duration relayBaseBackoff;

  @Value("${trellis.events.relay.max-backoff:PT5M}")
  private Duration relayMaxBackoff;

  @Value("${trellis.events.relay.send-timeout:PT5S}")
  private Duration relaySendTimeout;

  @Value("${trellis.events.relay.metrics-interval:PT15S}")
  private Duration relayMetricsInterval;

  @Value("${trellis.events.consumer.group-prefix:trellis.}")
  private String consumerGroupPrefix;

  @Value("${trellis.events.consumer.max-attempts:4}")
  private int consumerMaxAttempts;

  @Value("${trellis.events.consumer.base-backoff:PT1S}")
  private Duration consumerBaseBackoff;

  @Value("${trellis.events.consumer.max-backoff:PT30S}")
  private Duration consumerMaxBackoff;

  @Value("${trellis.events.retention.published-for:P7D}")
  private Duration retentionPublishedFor;

  @Value("${trellis.events.retention.processed-for:P14D}")
  private Duration retentionProcessedFor;

  @Value("${trellis.events.retention.batch-size:5000}")
  private int retentionBatchSize;

  @Value("${trellis.events.retention.cron:0 41 3 * * *}")
  private String retentionCron;

  public EventsProperties(
      String transport,
      String producer,
      String topicPrefix,
      Relay relay,
      Consumer consumer,
      Retention retention) {
    this.transport = transport;
    this.producer = producer;
    this.topicPrefix = topicPrefix;
    this.relayEnabled = relay.enabled();
    this.relayShards = relay.shards();
    this.relayBatchSize = relay.batchSize();
    this.relayPollInterval = relay.pollInterval();
    this.relayMaxPassesPerTick = relay.maxPassesPerTick();
    this.relayMaxAttempts = relay.maxAttempts();
    this.relayBaseBackoff = relay.baseBackoff();
    this.relayMaxBackoff = relay.maxBackoff();
    this.relaySendTimeout = relay.sendTimeout();
    this.relayMetricsInterval = relay.metricsInterval();
    this.consumerGroupPrefix = consumer.groupPrefix();
    this.consumerMaxAttempts = consumer.maxAttempts();
    this.consumerBaseBackoff = consumer.baseBackoff();
    this.consumerMaxBackoff = consumer.maxBackoff();
    this.retentionPublishedFor = retention.publishedFor();
    this.retentionProcessedFor = retention.processedFor();
    this.retentionBatchSize = retention.batchSize();
    this.retentionCron = retention.cron();
  }

  public Relay relay() {
    return new Relay(
        relayEnabled,
        relayShards,
        relayBatchSize,
        relayPollInterval,
        relayMaxPassesPerTick,
        relayMaxAttempts,
        relayBaseBackoff,
        relayMaxBackoff,
        relaySendTimeout,
        relayMetricsInterval);
  }

  public Consumer consumer() {
    return new Consumer(
        consumerGroupPrefix, consumerMaxAttempts, consumerBaseBackoff, consumerMaxBackoff);
  }

  public Retention retention() {
    return new Retention(
        retentionPublishedFor, retentionProcessedFor, retentionBatchSize, retentionCron);
  }

  public static final String PREFIX = "trellis.events";
  public static final String TRANSPORT_KAFKA = "kafka";
  public static final String TRANSPORT_INMEMORY = "inmemory";

  /**
   * enabled: run the relay in this process (turn off on instances that only serve reads) shards:
   * number of ordering shards; change only while the outbox has no NEW rows batchSize: heads sent
   * per pass and shard pollInterval: delay between relay ticks maxPassesPerTick: passes per shard
   * per tick while rows keep flowing maxAttempts: row-caused failures before a row becomes FAILED
   * (and blocks its key) baseBackoff: backoff of the first retry; also the defer delay when the
   * broker is down maxBackoff: cap of the retry backoff sendTimeout: wait for broker
   * acknowledgements per pass metricsInterval: refresh of the outbox gauges
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Relay {
    private final boolean enabled;
    private final int shards;
    private final int batchSize;
    private final Duration pollInterval;
    private final int maxPassesPerTick;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final Duration sendTimeout;
    private final Duration metricsInterval;
  }

  /**
   * groupPrefix: Kafka consumer group = prefix + handler consumer name maxAttempts: total handler
   * attempts (same default as the Python worker) before the failure mode applies baseBackoff:
   * backoff of the first handler retry maxBackoff: cap of the handler retry backoff
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Consumer {
    private final String groupPrefix;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
  }

  /**
   * publishedFor: keep PUBLISHED outbox rows this long (short-range replay and forensics)
   * processedFor: keep inbox rows this long; must exceed the broker's topic retention batchSize:
   * rows deleted per statement, to keep locks and WAL small cron: schedule of the cleanup
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Retention {
    private final Duration publishedFor;
    private final Duration processedFor;
    private final int batchSize;
    private final String cron;
  }
}
