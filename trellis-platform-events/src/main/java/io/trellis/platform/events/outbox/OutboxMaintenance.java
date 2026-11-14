package io.trellis.platform.events.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.platform.events.EventsProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox gauges and retention.
 *
 * <ul>
 *   <li>{@code outbox_oldest_new_age_seconds}: alert when above 60 s for 5 min (relay stuck or a key blocked).
 *   <li>{@code outbox_rows{status}}: alert when FAILED &gt; 0; a FAILED row blocks its key until an operator acts.
 *   <li>Retention deletes PUBLISHED rows and inbox rows in batches, keeping locks and WAL small.
 * </ul>
 */
public class OutboxMaintenance {
    private static final Logger log = LoggerFactory.getLogger(OutboxMaintenance.class);
    private static final String[] STATUSES = {"NEW", "PUBLISHED", "FAILED"};

    private static final String OLDEST_NEW_AGE = """
            SELECT COALESCE(EXTRACT(EPOCH FROM (:now - MIN(created_at))), 0)::bigint
              FROM outbox_event WHERE status = 'NEW'
            """;
    private static final String COUNT_BY_STATUS = "SELECT status, count(*) AS n FROM outbox_event GROUP BY status";
    private static final String PURGE_PUBLISHED = """
            DELETE FROM outbox_event WHERE id IN (
                SELECT id FROM outbox_event WHERE status = 'PUBLISHED' AND created_at < :before LIMIT :batch)
            """;
    private static final String PURGE_PROCESSED = """
            DELETE FROM processed_event WHERE (consumer_name, event_id) IN (
                SELECT consumer_name, event_id FROM processed_event WHERE processed_at < :before LIMIT :batch)
            """;

    private final JdbcClient jdbc;
    private final EventsProperties.Retention retention;
    private final Clock clock;
    private final AtomicLong oldestNewAge = new AtomicLong();
    private final Map<String, AtomicLong> rows = new ConcurrentHashMap<>();

    OutboxMaintenance(JdbcClient jdbc, EventsProperties.Retention retention, MeterRegistry meters, Clock clock) {
        this.jdbc = jdbc;
        this.retention = retention;
        this.clock = clock;
        Gauge.builder("outbox.oldest.new.age", oldestNewAge, AtomicLong::get)
                .baseUnit("seconds")
                .description("Age of the oldest unpublished outbox row")
                .register(meters);
        for (String status : STATUSES) {
            AtomicLong value = rows.computeIfAbsent(status, s -> new AtomicLong());
            Gauge.builder("outbox.rows", value, AtomicLong::get)
                    .tag("status", status)
                    .register(meters);
        }
    }

    @Scheduled(fixedDelayString = "${trellis.events.relay.metrics-interval:PT15S}")
    public void refreshGauges() {
        oldestNewAge.set(jdbc.sql(OLDEST_NEW_AGE)
                .param("now", Timestamp.from(clock.instant()))
                .query(Long.class)
                .single());
        rows.values().forEach(v -> v.set(0));
        jdbc.sql(COUNT_BY_STATUS)
                .query((rs, i) -> {
                    rows.computeIfAbsent(rs.getString("status"), s -> new AtomicLong())
                            .set(rs.getLong("n"));
                    return null;
                })
                .list();
    }

    @Scheduled(cron = "${trellis.events.retention.cron:0 41 3 * * *}")
    public void purge() {
        long published = purgeLoop(PURGE_PUBLISHED, retention.publishedFor());
        long processed = purgeLoop(PURGE_PROCESSED, retention.processedFor());
        log.atInfo()
                .addKeyValue("outbox_rows", published)
                .addKeyValue("inbox_rows", processed)
                .log("Event retention finished");
    }

    private long purgeLoop(String sql, Duration keep) {
        Timestamp before = Timestamp.from(clock.instant().minus(keep));
        long total = 0;
        int deleted;
        do {
            deleted = jdbc.sql(sql)
                    .param("before", before)
                    .param("batch", retention.batchSize())
                    .update();
            total += deleted;
        } while (deleted == retention.batchSize());
        return total;
    }
}
