package io.trellis.learningcontent.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.learningcontent.api.dto.CourseStatus;
import io.trellis.learningcontent.api.security.CoursePermissions;
import io.trellis.learningcontent.api.security.CourseScopes;
import io.trellis.learningcontent.application.course.port.CourseFilter;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.learningcontent.infrastructure.persistence.entity.CourseEntity;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.authz.sql.GrantPredicate;
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
 * Implements the module's port. Single-row work is a native statement on {@link
 * CourseJpaRepository}; the list is a keyset page whose visibility clause is assembled from
 * independent alternatives - including the scoped grant condition the platform owns - and run as a
 * native query that still returns entities (ADR 0004).
 */
@Repository
@RequiredArgsConstructor
class JpaCourseRepository implements CourseRepository {
  private static final String READ_GRANT_PARAMETERS = "read";
  private static final String SCOPED_READ_VISIBILITY =
      " AND (c.status = :readStatus OR c.owner_id = :readOwner)";
  private static final String COURSE_ID_COLUMN = "c.id";
  private static final String PAGE_SELECT = "SELECT c.* FROM course c WHERE TRUE";
  private static final String STATUS_VISIBLE = "c.status IN (:visibleStatuses)";
  private static final String OWNED_BY_CALLER = "c.owner_id = :ownerId";
  private static final String NOTHING_VISIBLE = " AND FALSE ";
  private static final String STATUS_IS = " AND c.status = :status ";
  private static final String NAME_CONTAINS = " AND c.name ILIKE :namePattern ESCAPE '\\' ";
  private static final KeysetQuery KEYSET =
      KeysetQuery.descending(
          SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.UUID_KEY));

  @PersistenceContext private EntityManager entityManager;

  private final CourseJpaRepository courses;
  private final CursorCodec cursors;

  @Override
  public Optional<Course> findByCode(CourseCode code) {
    return courses.findByCode(code.value()).map(CourseEntities::toDomain);
  }

  @Override
  public void insert(Course course) {
    entityManager.persist(CourseEntities.toEntity(course));
  }

  @Override
  public void update(Course next) {
    int rows =
        courses.updateIfVersionMatches(
            next.id(),
            next.details().name(),
            next.details().description(),
            next.status().name(),
            next.version(),
            next.updatedAt(),
            next.activatedAt(),
            next.archivedAt(),
            next.version() - 1);
    JdbcSupport.requireUpdated(rows, Course.AGGREGATE_TYPE, next.code().value());
  }

  @Override
  public CursorPage<Course> page(CourseFilter filter, String after, int limit) {
    Cursor cursor = after == null ? null : cursors.decode(after);
    StringBuilder sql = new StringBuilder(PAGE_SELECT);
    Map<String, Object> params = new HashMap<>(KEYSET.parameters(cursor, limit));
    if (!filter.unrestricted()) {
      List<String> visibleWhen = new ArrayList<>();
      if (!filter.visibleStatuses().isEmpty()) {
        visibleWhen.add(STATUS_VISIBLE);
        params.put("visibleStatuses", names(filter.visibleStatuses()));
      }
      if (filter.ownerId() != null) {
        visibleWhen.add(OWNED_BY_CALLER);
        params.put("ownerId", filter.ownerId());
      }
      if (filter.grantSubject() != null) {
        GrantPredicate.Fragment grant =
            GrantPredicate.coveredByGrant(
                COURSE_ID_COLUMN,
                CoursePermissions.MANAGE,
                CourseScopes.COURSE,
                filter.grantSubject());
        visibleWhen.add(grant.sql());
        params.putAll(grant.params());
        GrantPredicate.Fragment readable =
            GrantPredicate.coveredByGrant(
                COURSE_ID_COLUMN,
                CoursePermissions.READ,
                CourseScopes.COURSE,
                filter.grantSubject(),
                READ_GRANT_PARAMETERS);
        // Scoped READ obeys the same ACTIVE/owner visibility rule as the detail guard.
        visibleWhen.add("(" + readable.sql() + SCOPED_READ_VISIBILITY + ")");
        params.putAll(readable.params());
        params.put("readStatus", CourseStatus.ACTIVE.name());
        params.put(
            "readOwner",
            filter.grantSubject().type() == GrantSubjectType.USER
                ? filter.grantSubject().id()
                : null);
      }
      sql.append(
          visibleWhen.isEmpty()
              ? NOTHING_VISIBLE
              : " AND (" + String.join(" OR ", visibleWhen) + ") ");
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
    Query query = entityManager.createNativeQuery(sql.toString(), CourseEntity.class);
    params.forEach(query::setParameter);
    List<Course> rows = new ArrayList<>();
    for (Object row : query.getResultList()) {
      rows.add(CourseEntities.toDomain((CourseEntity) row));
    }
    return KEYSET.page(rows, limit, c -> List.of(c.createdAt(), c.id()), cursors);
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
