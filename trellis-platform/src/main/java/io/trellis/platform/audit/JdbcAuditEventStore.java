package io.trellis.platform.audit;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keyset search over the audit trail; filters are applied before paging. */
public final class JdbcAuditEventStore implements AuditEventStore {
    private static final String PAGE_SELECT = """
            SELECT id, event_id, occurred_at, actor_type, actor_id, action,
                   aggregate_type, aggregate_id, correlation_id, trace_id
              FROM audit_event
             WHERE TRUE
            """;
    private static final String AGGREGATE_TYPE_IS = " AND aggregate_type = :aggregateType ";
    private static final String AGGREGATE_ID_IS = " AND aggregate_id = :aggregateId ";
    private static final String ACTOR_ID_IS = " AND actor_id = :actorId ";
    private static final String OCCURRED_FROM = " AND occurred_at >= :occurredFrom ";
    private static final String OCCURRED_TO = " AND occurred_at < :occurredTo ";
    private static final KeysetQuery KEYSET = KeysetQuery.descending(SortKey.of("id", KeyType.LONG));

    private static final RowMapper<AuditEventRecord> MAPPER = (rs, row) -> new AuditEventRecord(
            rs.getLong("id"),
            rs.getObject("event_id", java.util.UUID.class),
            JdbcSupport.instant(rs, "occurred_at"),
            rs.getString("actor_type"),
            rs.getString("actor_id"),
            rs.getString("action"),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("correlation_id"),
            rs.getString("trace_id"));

    private final JdbcClient jdbc;
    private final CursorCodec cursors;

    public JdbcAuditEventStore(JdbcClient jdbc, CursorCodec cursors) {
        this.jdbc = jdbc;
        this.cursors = cursors;
    }

    @Override
    public CursorPage<AuditEventRecord> page(AuditEventFilter filter, String after, int limit) {
        Cursor cursor = after == null ? null : cursors.decode(after);
        StringBuilder sql = new StringBuilder(PAGE_SELECT);
        Map<String, Object> params = new HashMap<>(KEYSET.parameters(cursor, limit));
        append(sql, params, AGGREGATE_TYPE_IS, "aggregateType", filter.aggregateType());
        append(sql, params, AGGREGATE_ID_IS, "aggregateId", filter.aggregateId());
        append(sql, params, ACTOR_ID_IS, "actorId", filter.actorId());
        if (filter.occurredFrom() != null) {
            sql.append(OCCURRED_FROM);
            params.put("occurredFrom", Timestamp.from(filter.occurredFrom()));
        }
        if (filter.occurredTo() != null) {
            sql.append(OCCURRED_TO);
            params.put("occurredTo", Timestamp.from(filter.occurredTo()));
        }
        sql.append(KEYSET.where(cursor, "AND")).append(KEYSET.orderBy()).append(KEYSET.limit());
        List<AuditEventRecord> rows =
                jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
        return KEYSET.page(rows, limit, event -> List.of(event.id()), cursors);
    }

    private static void append(
            StringBuilder sql, Map<String, Object> params, String fragment, String name, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(fragment);
            params.put(name, value);
        }
    }
}
