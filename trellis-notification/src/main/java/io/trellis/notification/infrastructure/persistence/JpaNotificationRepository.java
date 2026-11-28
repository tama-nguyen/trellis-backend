package io.trellis.notification.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.notification.application.port.NotificationFilter;
import io.trellis.notification.application.port.NotificationRepository;
import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.value.NotificationCode;
import io.trellis.notification.infrastructure.persistence.entity.NotificationEntity;
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
 * NotificationJpaRepository}; the list is the keyset page of {@link NotificationListQuery}, run as
 * a native query that still returns entities - one mapping, no second row mapper (ADR 0004).
 */
@Repository
@RequiredArgsConstructor
class JpaNotificationRepository implements NotificationRepository {
  private static final KeysetQuery KEYSET =
      KeysetQuery.descending(
          SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.LONG));

  @PersistenceContext private EntityManager entityManager;

  private final NotificationJpaRepository notifications;
  private final CursorCodec cursors;

  @Override
  public Optional<Notification> findByCode(final NotificationCode code) {
    return notifications.findByCode(code.value()).map(NotificationEntities::toDomain);
  }

  @Override
  public Notification create(final Notification.Draft draft) {
    final NotificationEntity entity = NotificationEntities.toEntity(draft);
    return NotificationEntities.toDomain(notifications.save(entity));
  }

  @Override
  public void update(final Notification next) {
    final int rows =
        notifications.updateIfVersionMatches(
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
    JdbcSupport.requireUpdated(rows, Notification.AGGREGATE_TYPE, next.code().value());
  }

  @Override
  public CursorPage<Notification> page(
      final NotificationFilter filter, final String after, final int limit) {
    final Cursor cursor = after == null ? null : cursors.decode(after);
    final FilteredSelect select = NotificationListQuery.of(filter).seek(KEYSET, cursor, limit);

    return KEYSET.page(
        fetch(select),
        limit,
        notification -> List.of(notification.createdAt(), notification.id()),
        cursors);
  }

  private List<Notification> fetch(final FilteredSelect select) {
    final Query query = entityManager.createNativeQuery(select.sql(), NotificationEntity.class);
    select.parameters().forEach(query::setParameter);

    final List<Notification> rows = new ArrayList<>();
    for (final Object row : query.getResultList()) {
      rows.add(NotificationEntities.toDomain((NotificationEntity) row));
    }
    return rows;
  }
}
