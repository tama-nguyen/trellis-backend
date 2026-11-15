package io.trellis.course.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.course.api.dto.CourseStatus;
import io.trellis.course.application.port.CourseFilter;
import io.trellis.course.application.port.CourseRepository;
import io.trellis.course.domain.model.Course;
import io.trellis.course.domain.model.CourseDetails;
import io.trellis.course.domain.value.CourseCode;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Native SQL over {@code JdbcClient}; optimistic writes on {@code version}; keyset pages on (created_at, id). */
@Repository
class JdbcCourseRepository implements CourseRepository {
    private static final String COLUMNS = """
            id, code, name, description, status, owner_id, version,
            created_at, updated_at, activated_at, archived_at
            """;
    private static final String FIND_BY_CODE = "SELECT " + COLUMNS + " FROM course WHERE code = :code";
    private static final String INSERT = """
            INSERT INTO course (id, code, name, description, status, owner_id, version,
                                created_at, updated_at, activated_at, archived_at)
            VALUES (:id, :code, :name, :description, :status, :ownerId, :version,
                    :createdAt, :updatedAt, :activatedAt, :archivedAt)
            """;
    private static final String UPDATE = """
            UPDATE course
               SET name = :name, description = :description, status = :status, version = :version,
                   updated_at = :updatedAt, activated_at = :activatedAt, archived_at = :archivedAt
             WHERE id = :id AND version = :expectedVersion
            """;
    private static final String PAGE_SELECT = "SELECT " + COLUMNS + " FROM course WHERE TRUE";
    private static final String VISIBLE = " AND (status IN (:visibleStatuses) OR owner_id = :ownerId) ";
    private static final String VISIBLE_NO_OWNER = " AND status IN (:visibleStatuses) ";
    private static final String STATUS_IS = " AND status = :status ";
    private static final String NAME_CONTAINS = " AND name ILIKE :namePattern ESCAPE '\\' ";
    private static final KeysetQuery KEYSET =
            KeysetQuery.descending(SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.UUID_KEY));

    private static final RowMapper<Course> MAPPER = (rs, row) -> new Course(
            rs.getObject("id", UUID.class),
            new CourseCode(rs.getString("code")),
            new CourseDetails(rs.getString("name"), rs.getString("description")),
            CourseStatus.valueOf(rs.getString("status")),
            rs.getString("owner_id"),
            rs.getLong("version"),
            JdbcSupport.instant(rs, "created_at"),
            JdbcSupport.instant(rs, "updated_at"),
            JdbcSupport.instant(rs, "activated_at"),
            JdbcSupport.instant(rs, "archived_at"));

    private final JdbcClient jdbc;
    private final CursorCodec cursors;

    JdbcCourseRepository(JdbcClient jdbc, CursorCodec cursors) {
        this.jdbc = jdbc;
        this.cursors = cursors;
    }

    @Override
    public Optional<Course> findByCode(CourseCode code) {
        return jdbc.sql(FIND_BY_CODE).param("code", code.value()).query(MAPPER).optional();
    }

    @Override
    public void insert(Course course) {
        jdbc.sql(INSERT).params(columns(course)).update();
    }

    @Override
    public void update(Course next) {
        Map<String, Object> params = columns(next);
        params.put("expectedVersion", next.version() - 1);
        int rows = jdbc.sql(UPDATE).params(params).update();
        JdbcSupport.requireUpdated(rows, Course.AGGREGATE_TYPE, next.code().value());
    }

    @Override
    public CursorPage<Course> page(CourseFilter filter, String after, int limit) {
        Cursor cursor = after == null ? null : cursors.decode(after);
        StringBuilder sql = new StringBuilder(PAGE_SELECT);
        Map<String, Object> params = new HashMap<>(KEYSET.parameters(cursor, limit));
        if (!filter.unrestricted()) {
            sql.append(filter.ownerId() == null ? VISIBLE_NO_OWNER : VISIBLE);
            params.put("visibleStatuses", names(filter.visibleStatuses()));
            if (filter.ownerId() != null) {
                params.put("ownerId", filter.ownerId());
            }
        }
        if (filter.status() != null) {
            sql.append(STATUS_IS);
            params.put("status", filter.status().name());
        }
        if (filter.nameContains() != null) {
            sql.append(NAME_CONTAINS);
            params.put("namePattern", "%" + escapeLike(filter.nameContains()) + "%");
        }
        sql.append(KEYSET.where(cursor, "AND")).append(KEYSET.orderBy()).append(KEYSET.limit());
        List<Course> rows =
                jdbc.sql(sql.toString()).params(params).query(MAPPER).list();
        return KEYSET.page(rows, limit, c -> List.of(c.createdAt(), c.id()), cursors);
    }

    private static Map<String, Object> columns(Course c) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", c.id());
        params.put("code", c.code().value());
        params.put("name", c.details().name());
        params.put("description", c.details().description());
        params.put("status", c.status().name());
        params.put("ownerId", c.ownerId());
        params.put("version", c.version());
        params.put("createdAt", JdbcSupport.timestamp(c.createdAt()));
        params.put("updatedAt", JdbcSupport.timestamp(c.updatedAt()));
        params.put("activatedAt", JdbcSupport.timestamp(c.activatedAt()));
        params.put("archivedAt", JdbcSupport.timestamp(c.archivedAt()));
        return params;
    }

    private static List<String> names(Set<? extends Enum<?>> values) {
        List<String> names = new ArrayList<>();
        values.forEach(v -> names.add(v.name()));
        return names;
    }

    /** User text inside LIKE must not act as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
