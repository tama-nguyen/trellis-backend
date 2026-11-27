package io.trellis.notification.application.port;

import io.trellis.notification.application.view.NotificationStatsView;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Port to the {@code notification_stats} read model. */
public interface NotificationStatsRepository {
  /**
   * Stats of the given codes; codes without a row are absent from the map. One query for any number
   * of codes.
   */
  Map<String, NotificationStatsView> findByCodes(Collection<String> codes);

  /** Counts one publication. Not idempotent by itself: callers dedupe through the inbox. */
  void recordPublished(String code, Instant publishedAt);

  /**
   * Recomputes every row from the notification table in keyset batches; returns notifications
   * processed.
   */
  long rebuildFromNotifications(int batchSize);
}
