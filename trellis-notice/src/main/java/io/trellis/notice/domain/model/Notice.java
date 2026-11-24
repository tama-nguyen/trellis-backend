package io.trellis.notice.domain.model;

import io.trellis.common.error.ConflictException;
import io.trellis.notice.api.dto.NoticeStatus;
import io.trellis.notice.api.error.NoticeErrorCode;
import io.trellis.notice.domain.value.NoticeCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The Notice aggregate. Immutable: each transition returns the next state with {@code version + 1};
 * the repository writes it only if the stored version is still the one that was read (optimistic
 * concurrency).
 *
 * @param version starts at 1, increases by one per change; exposed as the ETag
 * @param publishCount publications so far (re-publishing an archived notice counts again)
 */
public record Notice(
    UUID id,
    NoticeCode code,
    NoticeContent content,
    NoticeStatus status,
    String authorId,
    long version,
    int publishCount,
    Instant createdAt,
    Instant updatedAt,
    Instant firstPublishedAt,
    Instant publishedAt,
    String publishedBy,
    Instant archivedAt) {
  public static final String AGGREGATE_TYPE = "Notice";
  static final long FIRST_VERSION = 1;

  public Notice {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(authorId, "authorId");
  }

  public static Notice draft(
      UUID id, NoticeCode code, NoticeContent content, String authorId, Instant now) {
    return new Notice(
        id,
        code,
        content,
        NoticeStatus.DRAFT,
        authorId,
        FIRST_VERSION,
        0,
        now,
        now,
        null,
        null,
        null,
        null);
  }

  /**
   * Drafts and published notices can be edited; an archived notice is frozen until re-published.
   */
  public Notice edit(NoticeContent newContent, Instant now) {
    require(status != NoticeStatus.ARCHIVED, "An archived notice cannot be edited");
    return new Notice(
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

  public Notice publish(String publisherId, Instant now) {
    require(status != NoticeStatus.PUBLISHED, "The notice is already published");
    return new Notice(
        id,
        code,
        content,
        NoticeStatus.PUBLISHED,
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

  public Notice archive(Instant now) {
    require(status == NoticeStatus.PUBLISHED, "Only a published notice can be archived");
    return new Notice(
        id,
        code,
        content,
        NoticeStatus.ARCHIVED,
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
      throw new ConflictException(NoticeErrorCode.NOTICE_INVALID_TRANSITION, message);
    }
  }
}
