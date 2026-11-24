package io.trellis.platform.audit;

import java.time.Instant;

/** Optional filters for audit-event search. */
public record AuditEventFilter(
    String aggregateType,
    String aggregateId,
    String actorId,
    Instant occurredFrom,
    Instant occurredTo) {}
