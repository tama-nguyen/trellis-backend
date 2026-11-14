package io.trellis.notice.application.port;

import io.trellis.notice.application.view.NoticeStatsView;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Port to the {@code notice_stats} read model. */
public interface NoticeStatsRepository {
    /** Stats of the given codes; codes without a row are absent from the map. One query for any number of codes. */
    Map<String, NoticeStatsView> findByCodes(Collection<String> codes);

    /** Counts one publication. Not idempotent by itself: callers dedupe through the inbox. */
    void recordPublished(String code, Instant publishedAt);

    /** Recomputes every row from the notice table in keyset batches; returns notices processed. */
    long rebuildFromNotices(int batchSize);
}
