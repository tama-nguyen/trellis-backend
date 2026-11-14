package io.trellis.platform.aspect.audit;

import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes {@code audit_event}. MANDATORY propagation: an audit row outside the business transaction is a lie. */
public class JdbcAuditRecorder implements AuditRecorder {
    private static final String INSERT = """
            INSERT INTO audit_event (event_id, occurred_at, actor_type, actor_id, action, aggregate_type,
                                     aggregate_id, correlation_id, trace_id)
            VALUES (:eventId, :occurredAt, :actorType, :actorId, :action, :aggregateType,
                    :aggregateId, :correlationId, :traceId)
            """;

    private final JdbcClient jdbc;

    public JdbcAuditRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        jdbc.sql(INSERT)
                .param("eventId", entry.eventId())
                .param("occurredAt", Timestamp.from(entry.occurredAt()))
                .param("actorType", entry.context().actor().type().name())
                .param("actorId", entry.context().actor().id())
                .param("action", entry.action())
                .param("aggregateType", entry.aggregateType())
                .param("aggregateId", entry.aggregateId())
                .param("correlationId", entry.context().correlationId())
                .param("traceId", entry.context().traceId())
                .update();
    }
}
