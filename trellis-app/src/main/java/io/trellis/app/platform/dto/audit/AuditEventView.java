package io.trellis.app.platform.dto.audit;

import io.trellis.platform.audit.AuditEventRecord;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Operator view of one audit event. */
public record AuditEventView(
    @NotNull long id,
    @NotNull UUID eventId,
    @NotNull Instant occurredAt,
    @NotNull String actorType,
    @NotNull String actorId,
    @NotNull String action,
    @NotNull String aggregateType,
    String aggregateId,
    @NotNull String correlationId,
    String traceId) {
  public static AuditEventView of(AuditEventRecord event) {
    return new AuditEventView(
        event.id(),
        event.eventId(),
        event.occurredAt(),
        event.actorType(),
        event.actorId(),
        event.action(),
        event.aggregateType(),
        event.aggregateId(),
        event.correlationId(),
        event.traceId());
  }
}
