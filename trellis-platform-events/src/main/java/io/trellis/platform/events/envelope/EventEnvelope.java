package io.trellis.platform.events.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The message body of every event, field for field {@code contracts/events/envelope.schema.json}.
 * Consumers are tolerant readers: unknown fields are ignored, so additive envelope changes never
 * break them.
 *
 * <p>Every component is named after its contract field except {@code originator}, which the
 * contract still calls {@code actor}. Renaming the field would break every consumer in the Python
 * and TypeScript repos at once, so the clearer name is kept to Java and pinned to the wire name by
 * {@link JsonProperty}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    String aggregateId,
    String partitionKey,
    Instant occurredAt,
    String producer,
    String correlationId,
    String traceparent,
    @JsonProperty(ORIGINATOR_FIELD) EventOriginator originator,
    JsonNode payload) {
  /** Contract name of the {@link #originator()} field; see the class comment. */
  public static final String ORIGINATOR_FIELD = "actor";
}
