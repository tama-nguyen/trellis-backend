package io.trellis.platform.events.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@code processed_event} insert with ON CONFLICT DO NOTHING; counts duplicates per consumer. */
public class JdbcInboxGuard implements InboxGuard {
  private static final String INSERT =
      """
            INSERT INTO processed_event (consumer_name, event_id, correlation_id, processed_at)
            VALUES (:consumer, :eventId, :correlationId, :now)
            ON CONFLICT DO NOTHING
            """;
  private static final String DUPLICATE_METRIC = "inbox.duplicate";

  private final JdbcClient jdbc;
  private final MeterRegistry meters;
  private final Clock clock;

  public JdbcInboxGuard(JdbcClient jdbc, MeterRegistry meters, Clock clock) {
    this.jdbc = jdbc;
    this.meters = meters;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean firstDelivery(String consumerName, UUID eventId, String correlationId) {
    int inserted =
        jdbc.sql(INSERT)
            .param("consumer", consumerName)
            .param("eventId", eventId)
            .param("correlationId", correlationId)
            .param("now", Timestamp.from(clock.instant()))
            .update();
    if (inserted == 0) {
      meters.counter(DUPLICATE_METRIC, "consumer", consumerName).increment();
    }
    return inserted == 1;
  }
}
