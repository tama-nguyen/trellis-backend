package io.trellis.notice.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.notice.application.port.NoticeFilter;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.value.NoticeCode;
import io.trellis.notice.infrastructure.persistence.entity.NoticeEntity;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the module's port. Single-row work is a native statement on {@link NoticeJpaRepository}; the list
 * is a keyset page, whose WHERE clause depends on who is asking, so it is built here and run as a native query
 * that still returns entities - one mapping, no second row mapper (ADR 0004).
 */
@Repository
@RequiredArgsConstructor
class JpaNoticeRepository implements NoticeRepository {
    private static final String PAGE_SELECT = "SELECT n.* FROM notice n WHERE TRUE";
    private static final String VISIBLE = """
             AND ((n.status IN (:visibleStatuses) AND n.audience IN (:visibleAudiences))
                  OR n.author_id = :ownerId)
            """;
    private static final String VISIBLE_NO_OWNER =
            " AND (n.status IN (:visibleStatuses) AND n.audience IN (:visibleAudiences)) ";
    private static final String AUDIENCE_IS = " AND n.audience = :audience ";
    private static final String TITLE_CONTAINS = " AND n.title ILIKE :titlePattern ESCAPE '\\' ";
    private static final KeysetQuery KEYSET =
            KeysetQuery.descending(SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.UUID_KEY));

    @PersistenceContext
    private EntityManager entityManager;

    private final NoticeJpaRepository notices;
    private final CursorCodec cursors;

    @Override
    public Optional<Notice> findByCode(NoticeCode code) {
        return notices.findByCode(code.value()).map(NoticeEntities::toDomain);
    }

    @Override
    public void insert(Notice notice) {
        entityManager.persist(NoticeEntities.toEntity(notice));
    }

    @Override
    public void update(Notice next) {
        int rows = notices.updateIfVersionMatches(
                next.id(),
                next.content().title(),
                next.content().body(),
                next.content().severity().name(),
                next.content().audience().name(),
                next.status().name(),
                next.version(),
                next.publishCount(),
                next.updatedAt(),
                next.firstPublishedAt(),
                next.publishedAt(),
                next.publishedBy(),
                next.archivedAt(),
                next.version() - 1);
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
        Query query = entityManager.createNativeQuery(sql.toString(), NoticeEntity.class);
        params.forEach(query::setParameter);
        List<Notice> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            rows.add(NoticeEntities.toDomain((NoticeEntity) row));
        }
        return KEYSET.page(rows, limit, n -> List.of(n.createdAt(), n.id()), cursors);
    }

    private static List<String> names(Set<? extends Enum<?>> values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.name()));
        return result;
    }

    /** User text inside LIKE must not act as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
