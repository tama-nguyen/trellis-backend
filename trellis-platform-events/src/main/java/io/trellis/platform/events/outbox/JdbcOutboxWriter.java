package io.trellis.platform.events.outbox;

import io.trellis.common.context.Actor;
import io.trellis.common.context.ActorType;
import io.trellis.common.context.RequestContext;
import io.trellis.common.context.TraceContextAccessor;
import io.trellis.common.id.UuidV7;
import io.trellis.platform.events.EventsProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Inserts into {@code outbox_event}. See {@link OutboxWriter} for the transaction rule. */
public class JdbcOutboxWriter implements OutboxWriter {
  static final String HEADER_CORRELATION_ID = "correlationId";
  static final String HEADER_TRACEPARENT = "traceparent";
  static final String HEADER_ACTOR_TYPE = "actorType";
  static final String HEADER_ACTOR_ID = "actorId";

  private static final String INSERT =
      """
            INSERT INTO outbox_event (event_id, event_type, event_version, aggregate_type, aggregate_id,
                                      partition_key, shard, topic, payload, headers, created_at, next_attempt_at)
            VALUES (:eventId, :eventType, :eventVersion, :aggregateType, :aggregateId,
                    :partitionKey, :shard, :topic, CAST(:payload AS jsonb), CAST(:headers AS jsonb), :now, :now)
            """;

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final TopicResolver topics;
  private final TraceContextAccessor trace;
  private final EventsProperties properties;
  private final Clock clock;

  public JdbcOutboxWriter(
      JdbcClient jdbc,
      JsonMapper json,
      TopicResolver topics,
      TraceContextAccessor trace,
      EventsProperties properties,
      Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.topics = topics;
    this.trace = trace;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID append(RequestContext ctx, OutboxMessage message) {
    UUID eventId = UuidV7.next(clock);
    jdbc.sql(INSERT)
        .param("eventId", eventId)
        .param("eventType", message.eventType())
        .param("eventVersion", message.eventVersion())
        .param("aggregateType", message.aggregateType())
        .param("aggregateId", message.aggregateId())
        .param("partitionKey", message.partitionKey())
        .param("shard", Shards.of(message.partitionKey(), properties.relay().shards()))
        .param("topic", topics.topicFor(message.eventType()))
        .param("payload", write(message.payload()))
        .param("headers", write(headers(ctx)))
        .param("now", Timestamp.from(clock.instant()))
        .update();
    return eventId;
  }

  private Map<String, String> headers(RequestContext ctx) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(HEADER_CORRELATION_ID, ctx.correlationId());
    trace.traceparent().ifPresent(tp -> headers.put(HEADER_TRACEPARENT, tp));
    Actor actor = ctx.actor();
    if (actor.type() != ActorType.ANONYMOUS) {
      headers.put(HEADER_ACTOR_TYPE, actor.type().name());
      headers.put(HEADER_ACTOR_ID, actor.id());
    }
    return headers;
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
          "Outbox value is not serialisable: " + value.getClass().getSimpleName(), e);
    }
  }
}
