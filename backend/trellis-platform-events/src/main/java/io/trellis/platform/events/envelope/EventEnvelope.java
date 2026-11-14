package io.trellis.platform.events.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The message body of every event, field for field {@code contracts/events/envelope.schema.json}. Consumers are
 * tolerant readers: unknown fields are ignored, so additive envelope changes never break them.
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
        EnvelopeActor actor,
        JsonNode payload) {}
