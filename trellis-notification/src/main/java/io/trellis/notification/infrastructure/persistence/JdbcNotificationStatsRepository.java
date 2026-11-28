package io.trellis.notification.infrastructure.persistence;

import io.trellis.notification.application.port.NotificationStatsRepository;
import io.trellis.notification.application.view.NotificationStatsView;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code notification_stats} projection. */
@Repository
@RequiredArgsConstructor
class JdbcNotificationStatsRepository implements NotificationStatsRepository {
  private static final String FIND_BY_CODES =
      """
            SELECT notification_code, publish_count, first_published_at, last_published_at
              FROM notification_stats WHERE notification_code IN (:codes)
            """;
  private static final String RECORD_PUBLISHED =
      """
            INSERT INTO notification_stats (notification_code, publish_count, first_published_at, last_published_at, updated_at)
            VALUES (:code, 1, :at, :at, now())
            ON CONFLICT (notification_code) DO UPDATE
               SET publish_count = notification_stats.publish_count + 1,
                   first_published_at = LEAST(notification_stats.first_published_at, EXCLUDED.first_published_at),
                   last_published_at = GREATEST(notification_stats.last_published_at, EXCLUDED.last_published_at),
                   updated_at = now()
            """;

  /**
   * One keyset batch of the rebuild: copy the source-of-truth columns of up to {@code :batch}
   * notifications after {@code :after}, and return the last id processed. Never-published
   * notifications get no row (and lose a stale one).
   */
  private static final String REBUILD_BATCH =
      """
            WITH batch AS (
                SELECT id, code, publish_count, first_published_at, published_at
                  FROM notification WHERE id > :after ORDER BY id LIMIT :batch
            ), removed AS (
                DELETE FROM notification_stats s USING batch b
                 WHERE s.notification_code = b.code AND b.publish_count = 0
            ), upserted AS (
                INSERT INTO notification_stats (notification_code, publish_count, first_published_at, last_published_at, updated_at)
                SELECT code, publish_count, first_published_at, published_at, now() FROM batch WHERE publish_count > 0
                ON CONFLICT (notification_code) DO UPDATE
                   SET publish_count = EXCLUDED.publish_count,
                       first_published_at = EXCLUDED.first_published_at,
                       last_published_at = EXCLUDED.last_published_at,
                       updated_at = now()
            )
            SELECT id FROM batch ORDER BY id
            """;

  /** Generated IDs are positive; zero starts a projection rebuild. */
  private static final Long BEFORE_FIRST = 0L;

  private static final RowMapper<NotificationStatsView> MAPPER =
      (rs, row) ->
          new NotificationStatsView(
              rs.getString("notification_code"),
              rs.getInt("publish_count"),
              JdbcSupport.instant(rs, "first_published_at"),
              JdbcSupport.instant(rs, "last_published_at"));

  private final JdbcClient jdbc;

  @Override
  public Map<String, NotificationStatsView> findByCodes(final Collection<String> codes) {
    if (codes.isEmpty()) {
      return Map.of();
    }
    return jdbc.sql(FIND_BY_CODES).param("codes", List.copyOf(codes)).query(MAPPER).list().stream()
        .collect(Collectors.toMap(NotificationStatsView::notificationCode, Function.identity()));
  }

  @Override
  public void recordPublished(final String code, final Instant publishedAt) {
    jdbc.sql(RECORD_PUBLISHED)
        .param("code", code)
        .param("at", JdbcSupport.timestamp(publishedAt))
        .update();
  }

  @Override
  public long rebuildFromNotifications(final int batchSize) {
    long processed = 0;
    Long after = BEFORE_FIRST;
    while (true) {
      final List<Long> ids =
          jdbc.sql(REBUILD_BATCH)
              .param("after", after)
              .param("batch", batchSize)
              .query(Long.class)
              .list();
      if (ids.isEmpty()) {
        return processed;
      }
      processed += ids.size();
      after = ids.getLast();
    }
  }
}
