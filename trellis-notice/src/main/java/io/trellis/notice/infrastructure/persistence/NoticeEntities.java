package io.trellis.notice.infrastructure.persistence;

import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.model.NoticeContent;
import io.trellis.notice.domain.value.NoticeCode;
import io.trellis.notice.infrastructure.persistence.entity.NoticeEntity;

/** The one place that converts between the immutable aggregate and the mutable row (ADR 0004). */
final class NoticeEntities {

    private NoticeEntities() {}

    static Notice toDomain(NoticeEntity e) {
        return new Notice(
                e.getId(),
                new NoticeCode(e.getCode()),
                new NoticeContent(e.getTitle(), e.getBody(), e.getSeverity(), e.getAudience()),
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

    static NoticeEntity toEntity(Notice n) {
        NoticeEntity e = new NoticeEntity();
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
