package io.trellis.platform.events.outbox;

import io.trellis.platform.persistence.jdbc.JdbcSupport;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/** A due head-of-key row read by the relay. */
record PendingRow(
    long id,
    UUID eventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    String aggregateId,
    String partitionKey,
    String topic,
    String payload,
    String headers,
    Instant createdAt,
    int attempts) {

  static final RowMapper<PendingRow> MAPPER =
      (rs, i) ->
          new PendingRow(
              rs.getLong("id"),
              rs.getObject("event_id", UUID.class),
              rs.getString("event_type"),
              rs.getInt("event_version"),
              rs.getString("aggregate_type"),
              rs.getString("aggregate_id"),
              rs.getString("partition_key"),
              rs.getString("topic"),
              rs.getString("payload"),
              rs.getString("headers"),
              JdbcSupport.instant(rs, "created_at"),
              rs.getInt("attempts"));
}
