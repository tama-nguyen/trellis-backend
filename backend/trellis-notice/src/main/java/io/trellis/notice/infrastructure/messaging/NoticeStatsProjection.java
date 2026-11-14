package io.trellis.notice.infrastructure.messaging;

import io.trellis.common.context.RequestContext;
import io.trellis.notice.api.event.NoticePublishedV1;
import io.trellis.notice.application.port.NoticeStatsRepository;
import io.trellis.platform.events.consumer.EventHandler;
import io.trellis.platform.events.consumer.InboundEvent;
import org.springframework.stereotype.Component;

/**
 * Java consumer of {@code notice.published}: keeps {@code notice_stats}. The dispatcher runs {@link #handle} in a
 * transaction that also inserts the inbox row, so a redelivered event (relay retry, replay) counts once. The
 * Python {@code notice-stats} worker consumes the same event under its own consumer name and inbox key.
 */
@Component
class NoticeStatsProjection implements EventHandler<NoticePublishedV1> {
    /** Inbox key and Kafka group suffix; renaming it re-counts the whole topic. */
    static final String CONSUMER_NAME = "notice-stats-projection";

    private final NoticeStatsRepository stats;

    NoticeStatsProjection(NoticeStatsRepository stats) {
        this.stats = stats;
    }

    @Override
    public String consumerName() {
        return CONSUMER_NAME;
    }

    @Override
    public String eventType() {
        return NoticePublishedV1.EVENT_TYPE;
    }

    @Override
    public Class<NoticePublishedV1> payloadType() {
        return NoticePublishedV1.class;
    }

    @Override
    public void handle(RequestContext ctx, InboundEvent<NoticePublishedV1> event) {
        NoticePublishedV1 payload = event.payload();
        stats.recordPublished(payload.noticeCode(), payload.publishedAt());
    }
}
