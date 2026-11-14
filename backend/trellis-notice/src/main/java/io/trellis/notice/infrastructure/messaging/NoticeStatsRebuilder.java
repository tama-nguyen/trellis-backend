package io.trellis.notice.infrastructure.messaging;

import io.trellis.common.context.RequestContext;
import io.trellis.notice.application.port.NoticeStatsRepository;
import io.trellis.platform.events.replay.ReadModelRebuilder;
import org.springframework.stereotype.Component;

/**
 * Recomputes {@code notice_stats} from the {@code notice} table (the source of truth), for when events were lost
 * beyond outbox retention or the projection had a bug. Idempotent: every batch overwrites. Triggered by an
 * operator: {@code POST /api/ops/read-models/notice-stats/rebuild}.
 */
@Component
class NoticeStatsRebuilder implements ReadModelRebuilder {
    static final String NAME = "notice-stats";
    private static final int BATCH_SIZE = 500;

    private final NoticeStatsRepository stats;

    NoticeStatsRebuilder(NoticeStatsRepository stats) {
        this.stats = stats;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public long rebuild(RequestContext ctx) {
        return stats.rebuildFromNotices(BATCH_SIZE);
    }
}
