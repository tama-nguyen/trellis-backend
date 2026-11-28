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

  static NotificationEntity toEntity(final Notification.Draft draft) {
    final NotificationEntity entity = new NotificationEntity();
    entity.setCode(draft.code().value());
    entity.setTitle(draft.content().title());
    entity.setBody(draft.content().body());
    entity.setSeverity(draft.content().severity());
    entity.setAudience(draft.content().audience());
    entity.setStatus(draft.status());
    entity.setAuthorId(draft.authorId());
    entity.setVersion(draft.version());
    entity.setPublishCount(draft.publishCount());
    entity.setCreatedAt(draft.createdAt());
    entity.setUpdatedAt(draft.updatedAt());
    return entity;
  }
}
