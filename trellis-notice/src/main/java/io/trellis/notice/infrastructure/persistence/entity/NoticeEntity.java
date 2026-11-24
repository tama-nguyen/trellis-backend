package io.trellis.notice.infrastructure.persistence.entity;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeSeverity;
import io.trellis.notice.api.dto.NoticeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row of {@code notice}.
 *
 * <p>{@code version} is a plain column, not {@code @Version}: the aggregate decides when it advances, and the
 * update statement checks it explicitly. Letting Hibernate own it would mean two writers of the same number.
 */
@Entity
@Table(name = "notice")
@Getter
@Setter
@NoArgsConstructor
public class NoticeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 17)
    private String code;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private NoticeSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 16)
    private NoticeAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NoticeStatus status;

    @Column(name = "author_id", nullable = false, length = 200)
    private String authorId;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "publish_count", nullable = false)
    private int publishCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "first_published_at")
    private Instant firstPublishedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 200)
    private String publishedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
