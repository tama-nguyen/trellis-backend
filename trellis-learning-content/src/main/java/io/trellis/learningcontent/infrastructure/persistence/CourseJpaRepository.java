package io.trellis.learningcontent.infrastructure.persistence;

import io.trellis.learningcontent.infrastructure.persistence.entity.CourseEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Native SQL over {@code course} (ADR 0004). Listing is a keyset page and lives in the adapter. */
public interface CourseJpaRepository extends JpaRepository<CourseEntity, UUID> {

  @Query(value = "SELECT c.* FROM course c WHERE c.code = :code", nativeQuery = true)
  Optional<CourseEntity> findByCode(@Param("code") String code);

  /** Optimistic write: the row moves only if it still holds the version the caller read. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
                    UPDATE course
                       SET name = :name,
                           description = :description,
                           status = :status,
                           version = :version,
                           updated_at = :updatedAt,
                           activated_at = :activatedAt,
                           archived_at = :archivedAt
                     WHERE id = :id
                       AND version = :expectedVersion
                    """,
      nativeQuery = true)
  int updateIfVersionMatches(
      @Param("id") UUID id,
      @Param("name") String name,
      @Param("description") String description,
      @Param("status") String status,
      @Param("version") long version,
      @Param("updatedAt") Instant updatedAt,
      @Param("activatedAt") Instant activatedAt,
      @Param("archivedAt") Instant archivedAt,
      @Param("expectedVersion") long expectedVersion);
}
