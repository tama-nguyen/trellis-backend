package io.trellis.notification.infrastructure.persistence.entity;

import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.api.dto.NotificationSeverity;
import io.trellis.notification.api.dto.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row of {@code notification}.
 *
 * <p>{@code version} is a plain column, not {@code @Version}: the aggregate decides when it
 * advances, and the update statement checks it explicitly. Letting Hibernate own it would mean two
 * writers of the same number.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_ids")
  @SequenceGenerator(
      name = "notification_ids",
      sequenceName = "notification_id_seq",
      allocationSize = 1)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "code", nullable = false, length = 17)
  private String code;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body", nullable = false, columnDefinition = "text")
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 16)
  private NotificationSeverity severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "audience", nullable = false, length = 16)
  private NotificationAudience audience;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private NotificationStatus status;

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
