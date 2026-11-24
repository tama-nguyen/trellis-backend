package io.trellis.notice.infrastructure.persistence;

import io.trellis.notice.application.port.NoticeStatsRepository;
import io.trellis.notice.application.view.NoticeStatsView;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code notice_stats} projection. */
@Repository
class JdbcNoticeStatsRepository implements NoticeStatsRepository {
  private static final String FIND_BY_CODES =
      """
            SELECT notice_code, publish_count, first_published_at, last_published_at
              FROM notice_stats WHERE notice_code IN (:codes)
            """;
  private static final String RECORD_PUBLISHED =
      """
            INSERT INTO notice_stats (notice_code, publish_count, first_published_at, last_published_at, updated_at)
            VALUES (:code, 1, :at, :at, now())
            ON CONFLICT (notice_code) DO UPDATE
               SET publish_count = notice_stats.publish_count + 1,
                   first_published_at = LEAST(notice_stats.first_published_at, EXCLUDED.first_published_at),
                   last_published_at = GREATEST(notice_stats.last_published_at, EXCLUDED.last_published_at),
                   updated_at = now()
            """;

  /**
   * One keyset batch of the rebuild: copy the source-of-truth columns of up to {@code :batch}
   * notices after {@code :after}, and return the last id processed. Never-published notices get no
   * row (and lose a stale one).
   */
  private static final String REBUILD_BATCH =
      """
            WITH batch AS (
                SELECT id, code, publish_count, first_published_at, published_at
                  FROM notice WHERE id > :after ORDER BY id LIMIT :batch
            ), removed AS (
                DELETE FROM notice_stats s USING batch b
                 WHERE s.notice_code = b.code AND b.publish_count = 0
            ), upserted AS (
                INSERT INTO notice_stats (notice_code, publish_count, first_published_at, last_published_at, updated_at)
                SELECT code, publish_count, first_published_at, published_at, now() FROM batch WHERE publish_count > 0
                ON CONFLICT (notice_code) DO UPDATE
                   SET publish_count = EXCLUDED.publish_count,
                       first_published_at = EXCLUDED.first_published_at,
                       last_published_at = EXCLUDED.last_published_at,
                       updated_at = now()
            )
            SELECT id FROM batch ORDER BY id
            """;

  /** Smallest UUID: the keyset starting point of a rebuild. */
  private static final UUID BEFORE_FIRST = new UUID(0, 0);

  private static final RowMapper<NoticeStatsView> MAPPER =
      (rs, row) ->
          new NoticeStatsView(
              rs.getString("notice_code"),
              rs.getInt("publish_count"),
              JdbcSupport.instant(rs, "first_published_at"),
              JdbcSupport.instant(rs, "last_published_at"));

  private final JdbcClient jdbc;

  JdbcNoticeStatsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<String, NoticeStatsView> findByCodes(Collection<String> codes) {
    if (codes.isEmpty()) {
      return Map.of();
    }
    return jdbc.sql(FIND_BY_CODES).param("codes", List.copyOf(codes)).query(MAPPER).list().stream()
        .collect(Collectors.toMap(NoticeStatsView::noticeCode, Function.identity()));
  }

  @Override
  public void recordPublished(String code, Instant publishedAt) {
    jdbc.sql(RECORD_PUBLISHED)
        .param("code", code)
        .param("at", JdbcSupport.timestamp(publishedAt))
        .update();
  }

  @Override
  public long rebuildFromNotices(int batchSize) {
    long processed = 0;
    UUID after = BEFORE_FIRST;
    while (true) {
      List<UUID> ids =
          jdbc.sql(REBUILD_BATCH)
              .param("after", after)
              .param("batch", batchSize)
              .query(UUID.class)
              .list();
      if (ids.isEmpty()) {
        return processed;
      }
      processed += ids.size();
      after = ids.getLast();
    }
  }
}
