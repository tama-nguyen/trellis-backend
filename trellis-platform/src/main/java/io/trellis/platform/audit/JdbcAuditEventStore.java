package io.trellis.platform.audit;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.FilteredSelect;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keyset search over the audit trail; filters are applied before paging. */
public final class JdbcAuditEventStore implements AuditEventStore {
  private static final String PAGE_SELECT =
      """
            SELECT id, event_id, occurred_at, actor_type, actor_id, action,
                   aggregate_type, aggregate_id, correlation_id, trace_id
              FROM audit_event
             WHERE TRUE
            """;
  private static final String AGGREGATE_TYPE_IS = "aggregate_type = :aggregateType";
  private static final String AGGREGATE_ID_IS = "aggregate_id = :aggregateId";
  private static final String ACTOR_ID_IS = "actor_id = :actorId";
  private static final String OCCURRED_FROM = "occurred_at >= :occurredFrom";
  private static final String OCCURRED_TO = "occurred_at < :occurredTo";
  private static final KeysetQuery KEYSET = KeysetQuery.descending(SortKey.of("id", KeyType.LONG));

  private static final RowMapper<AuditEventRecord> MAPPER =
      (rs, row) ->
          new AuditEventRecord(
              rs.getLong("id"),
              rs.getObject("event_id", UUID.class),
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

  public JdbcAuditEventStore(final JdbcClient jdbc, final CursorCodec cursors) {
    this.jdbc = jdbc;
    this.cursors = cursors;
  }

  @Override
  public CursorPage<AuditEventRecord> page(
      final AuditEventFilter filter, final String after, final int limit) {
    final Cursor cursor = after == null ? null : cursors.decode(after);
    final FilteredSelect select = select(filter).seek(KEYSET, cursor, limit);
    final List<AuditEventRecord> rows =
        jdbc.sql(select.sql()).params(select.parameters()).query(MAPPER).list();

    return KEYSET.page(rows, limit, event -> List.of(event.id()), cursors);
  }

  private static FilteredSelect select(final AuditEventFilter filter) {
    return FilteredSelect.from(PAGE_SELECT)
        .andIfPresent(AGGREGATE_TYPE_IS, "aggregateType", filter.aggregateType())
        .andIfPresent(AGGREGATE_ID_IS, "aggregateId", filter.aggregateId())
        .andIfPresent(ACTOR_ID_IS, "actorId", filter.actorId())
        .andIfPresent(OCCURRED_FROM, "occurredFrom", filter.occurredFrom(), Timestamp::from)
        .andIfPresent(OCCURRED_TO, "occurredTo", filter.occurredTo(), Timestamp::from);
  }
}
