package io.trellis.platform.events.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.trellis.common.retry.Backoff;
import io.trellis.platform.events.EventsProperties;
import io.trellis.platform.events.transport.EventTransport;
import io.trellis.platform.events.transport.OutboundEvent;
import io.trellis.platform.events.transport.SendOutcome;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves NEW outbox rows to the broker, preserving order per partition key.
 *
 * <ul>
 *   <li><b>Head of key:</b> a row is due only if no older NEW or FAILED row with the same key
 *       exists. A row in backoff, or a FAILED row, therefore holds back the rest of its key instead
 *       of being overtaken.
 *   <li><b>Shards:</b> rows carry {@code shard = crc32(key) mod N}; each pass takes {@code
 *       pg_try_advisory_xact_lock(base + shard)}, so N instances relay N shards in parallel and two
 *       instances never interleave one key. The lock lives exactly as long as the pass's
 *       transaction.
 *   <li><b>Failures:</b> a row-caused rejection spends an attempt with backoff (FAILED after max
 *       attempts); a broker outage defers the rows without spending attempts.
 *   <li><b>At least once:</b> a crash after the broker acknowledged but before commit re-sends the
 *       rows.
 * </ul>
 *
 * Design: {@code plan/09-outbox-and-algorithms.md} A.3.5. Borrowed from the earlier TicketFlow
 * relay (head-of-key and broker-outage defer), generalised to shards.
 */
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  /** Fixed namespace of relay advisory-lock ids; shard number is added. */
  static final long LOCK_BASE = 0x0A_C3E0_0000L;

  static final String METRIC_SENT = "outbox.relay.sent";
  static final String METRIC_PASS = "outbox.relay.pass";
  private static final int LAST_ERROR_MAX = 2000;
  private static final String TRANSPORT_DOWN = "transport unavailable: ";

  private static final String CLAIM_SHARD = "SELECT pg_try_advisory_xact_lock(:lockId)";
  private static final String DUE_HEADS =
      """
            SELECT o.id, o.event_id, o.event_type, o.event_version, o.aggregate_type, o.aggregate_id,
                   o.partition_key, o.topic, o.payload::text AS payload, o.headers::text AS headers,
                   o.created_at, o.attempts
              FROM outbox_event o
             WHERE o.shard = :shard AND o.status = 'NEW' AND o.next_attempt_at <= :now
               AND NOT EXISTS (SELECT 1 FROM outbox_event p
                                WHERE p.partition_key = o.partition_key AND p.id < o.id
                                  AND p.status IN ('NEW', 'FAILED'))
             ORDER BY o.id
             LIMIT :batch
            """;
  private static final String MARK_PUBLISHED =
      "UPDATE outbox_event SET status = 'PUBLISHED', published_at = :now WHERE id IN (:ids)";
  private static final String RECORD_REJECTED =
      """
            UPDATE outbox_event
               SET attempts = attempts + 1, last_error = :error, next_attempt_at = :next,
                   status = CASE WHEN attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE 'NEW' END
             WHERE id = :id
            """;
  private static final String DEFER =
      "UPDATE outbox_event SET last_error = :error, next_attempt_at = :next WHERE id IN (:ids)";

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final EventTransport transport;
  private final EnvelopeCodec codec;
  private final EventsProperties.Relay properties;
  private final Backoff backoff;
  private final MeterRegistry meters;
  private final Timer passTimer;
  private final Clock clock;

  OutboxRelay(
      JdbcClient jdbc,
      TransactionTemplate tx,
      EventTransport transport,
      EnvelopeCodec codec,
      EventsProperties.Relay properties,
      MeterRegistry meters,
      Clock clock) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.transport = transport;
    this.codec = codec;
    this.properties = properties;
    this.backoff = new Backoff(properties.baseBackoff(), properties.maxBackoff());
    this.meters = meters;
    this.passTimer = meters.timer(METRIC_PASS, "transport", transport.name());
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${trellis.events.relay.poll-interval:PT0.5S}")
  public void tick() {
    for (int shard = 0; shard < properties.shards(); shard++) {
      for (int pass = 0; pass < properties.maxPassesPerTick(); pass++) {
        int s = shard;
        Integer sent = passTimer.record(() -> tx.execute(status -> onePass(s)));
        if (sent == null || sent == 0) {
          break; // idle, blocked, deferred, or the shard is held by another instance
        }
      }
    }
  }

  /** One transaction per shard pass: the advisory lock lives exactly as long as the pass. */
  int onePass(int shard) {
    Boolean claimed =
        jdbc.sql(CLAIM_SHARD).param("lockId", LOCK_BASE + shard).query(Boolean.class).single();
    if (!Boolean.TRUE.equals(claimed)) {
      return 0;
    }
    Instant now = clock.instant();
    List<PendingRow> heads =
        jdbc.sql(DUE_HEADS)
            .param("shard", shard)
            .param("now", Timestamp.from(now))
            .param("batch", properties.batchSize())
            .query(PendingRow.MAPPER)
            .list();
    if (heads.isEmpty()) {
      return 0;
    }
    List<OutboundEvent> outbound = new ArrayList<>(heads.size());
    for (PendingRow row : heads) {
      outbound.add(codec.toOutbound(row));
    }
    List<SendOutcome> outcomes = transport.send(outbound, properties.sendTimeout());

    List<Long> published = new ArrayList<>();
    List<Long> deferred = new ArrayList<>();
    String downError = null;
    for (int i = 0; i < heads.size(); i++) {
      PendingRow row = heads.get(i);
      switch (outcomes.get(i)) {
        case SendOutcome.Sent sent -> published.add(row.id());
        case SendOutcome.TransportDown down -> {
          deferred.add(row.id());
          downError = down.error();
        }
        case SendOutcome.RowRejected rejected -> recordRejected(row, rejected.error(), now);
      }
    }
    // A DB error below rolls the pass back; the rows are sent again next pass (at-least-once).
    if (!published.isEmpty()) {
      jdbc.sql(MARK_PUBLISHED).param("ids", published).param("now", Timestamp.from(now)).update();
    }
    if (!deferred.isEmpty()) {
      jdbc.sql(DEFER)
          .param("ids", deferred)
          .param("error", truncate(TRANSPORT_DOWN + downError))
          .param("next", Timestamp.from(now.plus(properties.baseBackoff())))
          .update();
      log.atWarn()
          .addKeyValue("deferred", deferred.size())
          .addKeyValue("error", downError)
          .log("Event transport unavailable; rows deferred without spending attempts");
    }
    count("sent", published.size());
    count("transport_down", deferred.size());
    count("rejected", heads.size() - published.size() - deferred.size());
    return published.size();
  }

  private void recordRejected(PendingRow row, String error, Instant now) {
    jdbc.sql(RECORD_REJECTED)
        .param("id", row.id())
        .param("error", truncate(error))
        .param("next", Timestamp.from(now.plus(backoff.delay(row.attempts()))))
        .param("maxAttempts", properties.maxAttempts())
        .update();
    log.atWarn()
        .addKeyValue("event_id", row.eventId())
        .addKeyValue("event_type", row.eventType())
        .addKeyValue("attempt", row.attempts() + 1)
        .addKeyValue("error", error)
        .log("Outbox row rejected by transport");
  }

  private void count(String outcome, int amount) {
    if (amount > 0) {
      meters.counter(METRIC_SENT, "outcome", outcome).increment(amount);
    }
  }

  private static String truncate(String text) {
    String value = String.valueOf(text);
    return value.length() <= LAST_ERROR_MAX ? value : value.substring(0, LAST_ERROR_MAX);
  }
}
