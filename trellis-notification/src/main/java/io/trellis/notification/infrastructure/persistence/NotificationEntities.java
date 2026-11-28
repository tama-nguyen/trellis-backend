package io.trellis.notification.infrastructure.persistence;

import io.trellis.notification.domain.model.Notification;
import io.trellis.notification.domain.model.NotificationContent;
import io.trellis.notification.domain.value.NotificationCode;
import io.trellis.notification.infrastructure.persistence.entity.NotificationEntity;

/** The one place that converts between the immutable aggregate and the mutable row (ADR 0004). */
final class NotificationEntities {

  private NotificationEntities() {}

  static Notification toDomain(final NotificationEntity entity) {
    return new Notification(
        entity.getId(),
        new NotificationCode(entity.getCode()),
        new NotificationContent(
            entity.getTitle(), entity.getBody(), entity.getSeverity(), entity.getAudience()),
        entity.getStatus(),
        entity.getAuthorId(),
        entity.getVersion(),
        entity.getPublishCount(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getFirstPublishedAt(),
        entity.getPublishedAt(),
        entity.getPublishedBy(),
        entity.getArchivedAt());
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
