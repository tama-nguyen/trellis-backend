package io.trellis.platform.audit;

import java.time.Instant;
import java.util.UUID;

/** Read model of one persisted audit event. */
public record AuditEventRecord(
    long id,
    UUID eventId,
    Instant occurredAt,
    String actorType,
    String actorId,
    String action,
    String aggregateType,
    String aggregateId,
    String correlationId,
    String traceId) {}
