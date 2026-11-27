package io.trellis.notification.application.view;

import io.trellis.notification.domain.model.Notification;

/** Aggregate to views. Kept in one place so REST and GraphQL can never disagree on a field. */
public final class NotificationViews {
  private NotificationViews() {}

  public static NotificationView view(Notification n) {
    return new NotificationView(
        n.code().value(),
        n.content().title(),
        n.content().body(),
        n.content().severity(),
        n.content().audience(),
        n.status(),
        n.version(),
        n.createdAt(),
        n.updatedAt(),
        n.publishedAt(),
        n.archivedAt());
  }

  public static NotificationSummary summary(Notification n) {
    return new NotificationSummary(
        n.code().value(),
        n.content().title(),
        n.content().severity(),
        n.content().audience(),
        n.status(),
        n.createdAt(),
        n.publishedAt());
  }
}
