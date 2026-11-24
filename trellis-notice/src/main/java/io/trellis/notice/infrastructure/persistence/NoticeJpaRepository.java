package io.trellis.notice.infrastructure.persistence;

import io.trellis.notice.infrastructure.persistence.entity.NoticeEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Native SQL over {@code notice} (ADR 0004). Listing is a keyset page and lives in the adapter. */
public interface NoticeJpaRepository extends JpaRepository<NoticeEntity, UUID> {

    @Query(value = "SELECT n.* FROM notice n WHERE n.code = :code", nativeQuery = true)
    Optional<NoticeEntity> findByCode(@Param("code") String code);

    /**
     * Optimistic write: the row moves only if it still holds the version the caller read. The caller turns a
     * zero row count into the conflict error, so a lost race never overwrites a newer state.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                    UPDATE notice
                       SET title = :title,
                           body = :body,
                           severity = :severity,
                           audience = :audience,
                           status = :status,
                           version = :version,
                           publish_count = :publishCount,
                           updated_at = :updatedAt,
                           first_published_at = :firstPublishedAt,
                           published_at = :publishedAt,
                           published_by = :publishedBy,
                           archived_at = :archivedAt
                     WHERE id = :id
                       AND version = :expectedVersion
                    """, nativeQuery = true)
    int updateIfVersionMatches(
            @Param("id") UUID id,
            @Param("title") String title,
            @Param("body") String body,
            @Param("severity") String severity,
            @Param("audience") String audience,
            @Param("status") String status,
            @Param("version") long version,
            @Param("publishCount") int publishCount,
            @Param("updatedAt") Instant updatedAt,
            @Param("firstPublishedAt") Instant firstPublishedAt,
            @Param("publishedAt") Instant publishedAt,
            @Param("publishedBy") String publishedBy,
            @Param("archivedAt") Instant archivedAt,
            @Param("expectedVersion") long expectedVersion);
}
