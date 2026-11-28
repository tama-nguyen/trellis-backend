package io.trellis.learningcontent.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.learningcontent.application.course.port.CourseFilter;
import io.trellis.learningcontent.application.course.port.CourseRepository;
import io.trellis.learningcontent.domain.course.model.Course;
import io.trellis.learningcontent.domain.course.value.CourseCode;
import io.trellis.learningcontent.infrastructure.persistence.entity.CourseEntity;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.FilteredSelect;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the module's port. Single-row work is a native statement on {@link
 * CourseJpaRepository}; the list is the keyset page of {@link CourseListQuery}, run as a native
 * query that still returns entities (ADR 0004).
 */
@Repository
@RequiredArgsConstructor
class JpaCourseRepository implements CourseRepository {
  private static final KeysetQuery KEYSET =
      KeysetQuery.descending(
          SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.LONG));

  @PersistenceContext private EntityManager entityManager;

  private final CourseJpaRepository courses;
  private final CursorCodec cursors;

  @Override
  public boolean existsById(final Long id) {
    return courses.existsById(id);
  }

  @Override
  public Optional<Course> findByCode(final CourseCode code) {
    return courses.findByCode(code.value()).map(CourseEntities::toDomain);
  }

  @Override
  public Course create(final Course.Draft draft) {
    final CourseEntity entity = CourseEntities.toEntity(draft);
    return CourseEntities.toDomain(courses.save(entity));
  }

  @Override
  public void update(final Course next) {
    final int rows =
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
  public CursorPage<Course> page(final CourseFilter filter, final String after, final int limit) {
    final Cursor cursor = after == null ? null : cursors.decode(after);
    final FilteredSelect select = CourseListQuery.of(filter).seek(KEYSET, cursor, limit);

    return KEYSET.page(
        fetch(select), limit, course -> List.of(course.createdAt(), course.id()), cursors);
  }

  private List<Course> fetch(final FilteredSelect select) {
    final Query query = entityManager.createNativeQuery(select.sql(), CourseEntity.class);
    select.parameters().forEach(query::setParameter);

    final List<Course> rows = new ArrayList<>();
    for (final Object row : query.getResultList()) {
      rows.add(CourseEntities.toDomain((CourseEntity) row));
    }
    return rows;
  }
}
