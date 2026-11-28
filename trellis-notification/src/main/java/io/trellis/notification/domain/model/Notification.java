package io.trellis.notification.domain.model;

import io.trellis.common.error.ConflictException;
import io.trellis.notification.api.dto.NotificationStatus;
import io.trellis.notification.api.error.NotificationErrorCode;
import io.trellis.notification.domain.value.NotificationCode;
import java.time.Instant;
import java.util.Objects;

/**
 * The Notification aggregate. Immutable: each transition returns the next state with {@code version
 * + 1}; the repository writes it only if the stored version is still the one that was read
 * (optimistic concurrency).
 *
 * @param version starts at 1, increases by one per change; exposed as the ETag
 * @param publishCount publications so far (re-publishing an archived notification counts again)
 */
public record Notification(
    Long id,
    NotificationCode code,
    NotificationContent content,
    NotificationStatus status,
    String authorId,
    long version,
    int publishCount,
    Instant createdAt,
    Instant updatedAt,
    Instant firstPublishedAt,
    Instant publishedAt,
    String publishedBy,
    Instant archivedAt) {
  public static final String AGGREGATE_TYPE = "Notification";
  private static final long FIRST_VERSION = 1;

  public Notification {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(authorId, "authorId");
  }

  public static Draft draft(
      final NotificationCode code,
      final NotificationContent content,
      final String authorId,
      final Instant now) {
    return new Draft(code, content, authorId, now);
  }

  public record Draft(
      NotificationCode code, NotificationContent content, String authorId, Instant createdAt) {
    public Draft {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(content, "content");
      Objects.requireNonNull(authorId, "authorId");
      Objects.requireNonNull(createdAt, "createdAt");
    }

    public NotificationStatus status() {
      return NotificationStatus.DRAFT;
    }

    public long version() {
      return FIRST_VERSION;
    }

    public Instant updatedAt() {
      return createdAt;
    }

    public int publishCount() {
      return 0;
    }
  }

  /**
   * Drafts and published notifications can be edited; an archived notification is frozen until
   * re-published.
   */
  public Notification edit(NotificationContent newContent, Instant now) {
    require(status != NotificationStatus.ARCHIVED, "An archived notification cannot be edited");
    return new Notification(
        id,
        code,
        newContent,
        status,
        authorId,
        version + 1,
        publishCount,
        createdAt,
        now,
        firstPublishedAt,
        publishedAt,
        publishedBy,
        archivedAt);
  }

  public Notification publish(String publisherId, Instant now) {
    require(status != NotificationStatus.PUBLISHED, "The notification is already published");
    return new Notification(
        id,
        code,
        content,
        NotificationStatus.PUBLISHED,
        authorId,
        version + 1,
        publishCount + 1,
        createdAt,
        now,
        firstPublishedAt == null ? now : firstPublishedAt,
        now,
        publisherId,
        null);
  }

  public Notification archive(Instant now) {
    require(
        status == NotificationStatus.PUBLISHED, "Only a published notification can be archived");
    return new Notification(
        id,
        code,
        content,
        NotificationStatus.ARCHIVED,
        authorId,
        version + 1,
        publishCount,
        createdAt,
        now,
        firstPublishedAt,
        publishedAt,
        publishedBy,
        now);
  }

  public boolean isAuthoredBy(String actorId) {
    return authorId.equals(actorId);
  }

  private static void require(boolean allowed, String message) {
    if (!allowed) {
      throw new ConflictException(NotificationErrorCode.NOTIFICATION_INVALID_TRANSITION, message);
    }
  }
}
