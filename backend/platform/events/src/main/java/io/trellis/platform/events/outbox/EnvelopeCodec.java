package io.trellis.platform.events.outbox;

import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.envelope.EnvelopeActor;
import io.trellis.platform.events.envelope.EventEnvelope;
import io.trellis.platform.events.transport.OutboundEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Outbox row to wire format: envelope JSON body plus the headers consumers read without parsing it. */
final class EnvelopeCodec {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private final JsonMapper json;
    private final String producer;

    EnvelopeCodec(JsonMapper json, String producer) {
        this.json = json;
        this.producer = producer;
    }

    OutboundEvent toOutbound(PendingRow row) {
        Map<String, String> stored = json.readValue(row.headers(), STRING_MAP);
        String correlationId = stored.get(JdbcOutboxWriter.HEADER_CORRELATION_ID);
        String traceparent = stored.get(JdbcOutboxWriter.HEADER_TRACEPARENT);
        String actorType = stored.get(JdbcOutboxWriter.HEADER_ACTOR_TYPE);
        EventEnvelope envelope = new EventEnvelope(
                row.eventId(),
                row.eventType(),
                row.eventVersion(),
                row.aggregateType(),
                row.aggregateId(),
                row.partitionKey().equals(row.aggregateId()) ? null : row.partitionKey(),
                row.createdAt(),
                producer,
                correlationId,
                traceparent,
                actorType == null ? null : new EnvelopeActor(actorType, stored.get(JdbcOutboxWriter.HEADER_ACTOR_ID)),
                json.readTree(row.payload()));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(EventHeaders.EVENT_ID, row.eventId().toString());
        headers.put(EventHeaders.EVENT_TYPE, row.eventType());
        headers.put(EventHeaders.EVENT_VERSION, String.valueOf(row.eventVersion()));
        if (correlationId != null) {
            headers.put(EventHeaders.CORRELATION_ID, correlationId);
        }
        if (traceparent != null) {
            headers.put(EventHeaders.TRACEPARENT, traceparent);
        }
        return new OutboundEvent(
                row.eventId(), row.topic(), row.partitionKey(), json.writeValueAsBytes(envelope), headers);
    }
}
