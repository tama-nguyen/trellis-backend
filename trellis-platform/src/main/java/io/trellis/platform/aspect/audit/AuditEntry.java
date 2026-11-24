package io.trellis.platform.aspect.audit;

import io.trellis.common.context.RequestContext;
import java.time.Instant;
import java.util.UUID;

/** One audit row. {@code aggregateId} may be null for actions on collections. */
public record AuditEntry(
    UUID eventId,
    Instant occurredAt,
    RequestContext context,
    String action,
    String aggregateType,
    String aggregateId) {}
