package io.trellis.notification.infrastructure.messaging;

import io.trellis.common.context.RequestContext;
import io.trellis.notification.application.port.NotificationStatsRepository;
import io.trellis.platform.events.replay.ReadModelRebuilder;
import org.springframework.stereotype.Component;

/**
 * Recomputes {@code notification_stats} from the {@code notification} table (the source of truth),
 * for when events were lost beyond outbox retention or the projection had a bug. Idempotent: every
 * batch overwrites. Triggered by an operator: {@code POST
 * /api/ops/read-models/notification-stats/rebuild}.
 */
@Component
class NotificationStatsRebuilder implements ReadModelRebuilder {
  static final String NAME = "notification-stats";
  private static final int BATCH_SIZE = 500;

  private final NotificationStatsRepository stats;

  NotificationStatsRebuilder(NotificationStatsRepository stats) {
    this.stats = stats;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public long rebuild(RequestContext ctx) {
    return stats.rebuildFromNotifications(BATCH_SIZE);
  }
}
