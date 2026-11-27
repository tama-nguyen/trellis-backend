package io.trellis.notification.infrastructure.messaging;

import io.trellis.common.context.RequestContext;
import io.trellis.notification.api.event.NotificationPublishedV1;
import io.trellis.notification.application.port.NotificationStatsRepository;
import io.trellis.platform.events.consumer.EventHandler;
import io.trellis.platform.events.consumer.InboundEvent;
import org.springframework.stereotype.Component;

/**
 * Java consumer of {@code notification.published}: keeps {@code notification_stats}. The dispatcher
 * runs {@link #handle} in a transaction that also inserts the inbox row, so a redelivered event
 * (relay retry, replay) counts once. The Python {@code notification-stats} worker consumes the same
 * event under its own consumer name and inbox key.
 */
@Component
class NotificationStatsProjection implements EventHandler<NotificationPublishedV1> {
  /** Inbox key and Kafka group suffix; renaming it re-counts the whole topic. */
  static final String CONSUMER_NAME = "notification-stats-projection";

  private final NotificationStatsRepository stats;

  NotificationStatsProjection(NotificationStatsRepository stats) {
    this.stats = stats;
  }

  @Override
  public String consumerName() {
    return CONSUMER_NAME;
  }

  @Override
  public String eventType() {
    return NotificationPublishedV1.EVENT_TYPE;
  }

  @Override
  public Class<NotificationPublishedV1> payloadType() {
    return NotificationPublishedV1.class;
  }

  @Override
  public void handle(RequestContext ctx, InboundEvent<NotificationPublishedV1> event) {
    NotificationPublishedV1 payload = event.payload();
    stats.recordPublished(payload.notificationCode(), payload.publishedAt());
  }
}
