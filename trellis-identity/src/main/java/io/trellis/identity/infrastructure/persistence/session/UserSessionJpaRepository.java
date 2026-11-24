package io.trellis.identity.infrastructure.persistence.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Native SQL over {@code user_session} (ADR 0004). */
public interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, UUID> {

  /**
   * Takes the row lock that makes refresh-token rotation safe: two refreshes with the same token
   * serialise, and the loser sees the rotated row.
   *
   * <p>The lock is written as {@code FOR UPDATE} in the statement rather than with {@code @Lock},
   * which Spring Data cannot apply to a native query. Only {@code user_session} is in the FROM
   * clause, so the user row is never locked along with it.
   */
  @Query(
      value =
          """
                    SELECT s.*
                      FROM user_session s
                     WHERE s.refresh_token_hash = :tokenHash
                       AND s.revoked_at IS NULL
                       AND s.refresh_expires_at > :now
                       FOR UPDATE
                    """,
      nativeQuery = true)
  Optional<UserSessionEntity> lockActiveByRefreshToken(
      @Param("tokenHash") String tokenHash, @Param("now") Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
                    UPDATE user_session
                       SET access_token_hash = :accessHash,
                           refresh_token_hash = :refreshHash,
                           access_expires_at = :accessExpiresAt,
                           refresh_expires_at = :refreshExpiresAt,
                           rotated_at = :rotatedAt
                     WHERE id = :id
                       AND revoked_at IS NULL
                    """,
      nativeQuery = true)
  int rotate(
      @Param("id") UUID id,
      @Param("accessHash") String accessHash,
      @Param("refreshHash") String refreshHash,
      @Param("accessExpiresAt") Instant accessExpiresAt,
      @Param("refreshExpiresAt") Instant refreshExpiresAt,
      @Param("rotatedAt") Instant rotatedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
                    UPDATE user_session
                       SET revoked_at = :revokedAt
                     WHERE refresh_token_hash = :tokenHash
                       AND revoked_at IS NULL
                    """,
      nativeQuery = true)
  int revokeByRefreshToken(
      @Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
                    UPDATE user_session
                       SET revoked_at = :revokedAt
                     WHERE user_id = :userId
                       AND revoked_at IS NULL
                    """,
      nativeQuery = true)
  int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
