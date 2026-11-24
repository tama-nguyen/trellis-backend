package io.trellis.identity.infrastructure.persistence.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every statement here is native SQL against the real tables (ADR 0004): the text in this file is
 * the text the database runs, so it can be pasted into psql and explained as written.
 *
 * <p>Enum columns are bound as text, because a native statement goes to the driver without
 * Hibernate's enum conversion. The caller passes {@code UserStatus.ACTIVE.name()}, never a bare
 * literal.
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

  @Query(value = "SELECT u.* FROM users u WHERE u.email = :email", nativeQuery = true)
  Optional<UserEntity> findByEmail(@Param("email") String email);

  /**
   * Resolves the user behind a live access token in one statement. Kept as a join rather than two
   * lookups because it runs on every request that carries a native token.
   */
  @Query(
      value =
          """
                    SELECT a.*
                      FROM user_session s
                      JOIN user a ON a.id = s.user_id
                     WHERE s.access_token_hash = :tokenHash
                       AND s.revoked_at IS NULL
                       AND s.access_expires_at > :now
                    """,
      nativeQuery = true)
  Optional<UserEntity> findByActiveAccessToken(
      @Param("tokenHash") String tokenHash, @Param("now") Instant now);

  /**
   * Guarded by the current status, so a replayed verification link changes nothing.
   *
   * <p>{@code flushAutomatically} and {@code clearAutomatically} are not decoration: a native
   * update is invisible to the persistence context, so without them a pending insert could run
   * after this statement, and an user already loaded would keep its stale status for the rest of
   * the transaction.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
                    UPDATE users
                       SET status = :activeStatus,
                           email_verified_at = :verifiedAt,
                           updated_at = :verifiedAt
                     WHERE id = :userId
                       AND status = :pendingStatus
                    """,
      nativeQuery = true)
  int markEmailVerified(
      @Param("userId") UUID userId,
      @Param("verifiedAt") Instant verifiedAt,
      @Param("activeStatus") String activeStatus,
      @Param("pendingStatus") String pendingStatus);
}
