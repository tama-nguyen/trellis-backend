package io.trellis.notification.infrastructure.persistence;

import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.model.NotificationContent;
import io.trellis.notification.domain.value.NotificationCode;
import io.trellis.notification.infrastructure.persistence.entity.NotificationEntity;

/** The one place that converts between the immutable aggregate and the mutable row (ADR 0004). */
final class NotificationEntities {

  private NotificationEntities() {}

  static Notification toDomain(NotificationEntity e) {
    return new Notification(
        e.getId(),
        new NotificationCode(e.getCode()),
        new NotificationContent(e.getTitle(), e.getBody(), e.getSeverity(), e.getAudience()),
        e.getStatus(),
        e.getAuthorId(),
        e.getVersion(),
        e.getPublishCount(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getFirstPublishedAt(),
        e.getPublishedAt(),
        e.getPublishedBy(),
        e.getArchivedAt());
  }

  static NotificationEntity toEntity(Notification n) {
    NotificationEntity e = new NotificationEntity();
    e.setId(n.id());
    e.setCode(n.code().value());
    e.setTitle(n.content().title());
    e.setBody(n.content().body());
    e.setSeverity(n.content().severity());
    e.setAudience(n.content().audience());
    e.setStatus(n.status());
    e.setAuthorId(n.authorId());
    e.setVersion(n.version());
    e.setPublishCount(n.publishCount());
    e.setCreatedAt(n.createdAt());
    e.setUpdatedAt(n.updatedAt());
    e.setFirstPublishedAt(n.firstPublishedAt());
    e.setPublishedAt(n.publishedAt());
    e.setPublishedBy(n.publishedBy());
    e.setArchivedAt(n.archivedAt());
    return e;
  }
}
