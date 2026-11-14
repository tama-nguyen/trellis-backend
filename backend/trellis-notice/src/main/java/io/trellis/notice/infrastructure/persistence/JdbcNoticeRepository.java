package io.trellis.notice.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;
import io.trellis.notice.api.dto.NoticeStatus;
import io.trellis.notice.application.port.NoticeFilter;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.model.NoticeContent;
import io.trellis.notice.domain.value.NoticeCode;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Native SQL over {@code JdbcClient}; optimistic writes on {@code version}; keyset pages on (created_at, id). */
@Repository
class JdbcNoticeRepository implements NoticeRepository {
    private static final String COLUMNS = """
            id, code, title, body, severity, audience, status, author_id, version, publish_count,
            created_at, updated_at, first_published_at, published_at, published_by, archived_at
            """;
    private static final String FIND_BY_CODE = "SELECT " + COLUMNS + " FROM notice WHERE code = :code";
    private static final String INSERT = """
            INSERT INTO notice (id, code, title, body, severity, audience, status, author_id, version, publish_count,
                                created_at, updated_at, first_published_at, published_at, published_by, archived_at)
            VALUES (:id, :code, :title, :body, :severity, :audience, :status, :authorId, :version, :publishCount,
                    :createdAt, :updatedAt, :firstPublishedAt, :publishedAt, :publishedBy, :archivedAt)
            """;
    private static final String UPDATE = """
            UPDATE notice
               SET title = :title, body = :body, severity = :severity, audience = :audience, status = :status,
                   version = :version, publish_count = :publishCount, updated_at = :updatedAt,
                   first_published_at = :firstPublishedAt, published_at = :publishedAt,
                   published_by = :publishedBy, archived_at = :archivedAt
             WHERE id = :id AND version = :expectedVersion
            """;
    private static final String PAGE_SELECT = "SELECT " + COLUMNS + " FROM notice WHERE TRUE";
    private static final String VISIBLE = """
             AND ((status IN (:visibleStatuses) AND audience IN (:visibleAudiences))
                  OR author_id = :ownerId)
            """;
    private static final String VISIBLE_NO_OWNER =
            " AND (status IN (:visibleStatuses) AND audience IN (:visibleAudiences)) ";
    private static final String AUDIENCE_IS = " AND audience = :audience ";
    private static final String TITLE_CONTAINS = " AND title ILIKE :titlePattern ESCAPE '\\' ";
    private static final KeysetQuery KEYSET =
            KeysetQuery.descending(SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.UUID_KEY));

    private static final RowMapper<Notice> MAPPER = (rs, row) -> new Notice(
            rs.getObject("id", UUID.class),
            new NoticeCode(rs.getString("code")),
            new NoticeContent(
                    rs.getString("title"),
                    rs.getString("body"),
                    NoticeSeverity.valueOf(rs.getString("severity")),
                    NoticeAudience.valueOf(rs.getString("audience"))),
            NoticeStatus.valueOf(rs.getString("status")),
            rs.getString("author_id"),
            rs.getLong("version"),
            rs.getInt("publish_count"),
            JdbcSupport.instant(rs, "created_at"),
            JdbcSupport.instant(rs, "updated_at"),
            JdbcSupport.instant(rs, "first_published_at"),
            JdbcSupport.instant(rs, "published_at"),
            rs.getString("published_by"),
            JdbcSupport.instant(rs, "archived_at"));

    private final JdbcClient jdbc;
    private final CursorCodec cursors;

    JdbcNoticeRepository(JdbcClient jdbc, CursorCodec cursors) {
        this.jdbc = jdbc;
        this.cursors = cursors;
    }

    @Override
    public Optional<Notice> findByCode(NoticeCode code) {
        return jdbc.sql(FIND_BY_CODE).param("code", code.value()).query(MAPPER).optional();
    }

    @Override
    public void insert(Notice notice) {
        jdbc.sql(INSERT).params(columns(notice)).update();
    }

    @Override
    public void update(Notice next) {
        Map<String, Object> params = columns(next);
        params.put("expectedVersion", next.version() - 1);
        int rows = jdbc.sql(UPDATE).params(params).update();
        JdbcSupport.requireUpdated(rows, Notice.AGGREGATE_TYPE, next.code().value());
    }

    @Override
    public CursorPage<Notice> page(NoticeFilter filter, String after, int limit) {
        Cursor cursor = after == null ? null : cursors.decode(after);
        StringBuilder sql = new StringBuilder(PAGE_SELECT);
        Map<String, Object> params = new HashMap<>(KEYSET.parameters(cursor, limit));
        if (!filter.unrestricted()) {
            sql.append(filter.ownerId() == null ? VISIBLE_NO_OWNER : VISIBLE);
            params.put("visibleStatuses", names(filter.visibleStatuses()));
            params.put("visibleAudiences", names(filter.visibleAudiences()));
            if (filter.ownerId() != null) {
                params.put("ownerId", filter.ownerId());
            }
        }
        if (filter.audience() != null) {
            sql.append(AUDIENCE_IS);
            params.put("audience", filter.audience().name());
        }
        if (filter.titleContains() != null) {
            sql.append(TITLE_CONTAINS);
            params.put("titlePattern", "%" + escapeLike(filter.titleContains()) + "%");
        }
        sql.append(KEYSET.where(cursor, "AND")).append(KEYSET.orderBy()).append(KEYSET.limit());
        List<Notice> rows =
                jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
        return KEYSET.page(rows, limit, n -> List.of(n.createdAt(), n.id()), cursors);
    }

    private static Map<String, Object> columns(Notice n) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", n.id());
        params.put("code", n.code().value());
        params.put("title", n.content().title());
        params.put("body", n.content().body());
        params.put("severity", n.content().severity().name());
        params.put("audience", n.content().audience().name());
        params.put("status", n.status().name());
        params.put("authorId", n.authorId());
        params.put("version", n.version());
        params.put("publishCount", n.publishCount());
        params.put("createdAt", JdbcSupport.timestamp(n.createdAt()));
        params.put("updatedAt", JdbcSupport.timestamp(n.updatedAt()));
        params.put("firstPublishedAt", JdbcSupport.timestamp(n.firstPublishedAt()));
        params.put("publishedAt", JdbcSupport.timestamp(n.publishedAt()));
        params.put("publishedBy", n.publishedBy());
        params.put("archivedAt", JdbcSupport.timestamp(n.archivedAt()));
        return params;
    }

    private static List<String> names(java.util.Set<? extends Enum<?>> values) {
        List<String> names = new ArrayList<>();
        values.forEach(v -> names.add(v.name()));
        return names;
    }

    /** User text inside LIKE must not act as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
