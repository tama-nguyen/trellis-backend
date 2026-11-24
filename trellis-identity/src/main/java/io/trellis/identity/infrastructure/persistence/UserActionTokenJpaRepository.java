package io.trellis.identity.infrastructure.persistence;

import io.trellis.identity.infrastructure.persistence.entity.UserActionTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Native SQL over {@code user_action_token} (ADR 0004). {@code tokenType} is bound as text, because a
 * native statement bypasses Hibernate's enum conversion.
 */
public interface UserActionTokenJpaRepository extends JpaRepository<UserActionTokenEntity, UUID> {

    /** Locked for the same reason as a session refresh: a token must be consumable exactly once. */
    @Query(value = """
                    SELECT t.*
                      FROM user_action_token t
                     WHERE t.token_hash = :tokenHash
                       AND t.token_type = :tokenType
                       AND t.consumed_at IS NULL
                       AND t.expires_at > :now
                       FOR UPDATE
                    """, nativeQuery = true)
    Optional<UserActionTokenEntity> lockActive(
            @Param("tokenHash") String tokenHash, @Param("tokenType") String tokenType, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                    UPDATE user_action_token
                       SET consumed_at = :consumedAt
                     WHERE id = :id
                       AND consumed_at IS NULL
                    """, nativeQuery = true)
    int consume(@Param("id") UUID id, @Param("consumedAt") Instant consumedAt);

    /** Issuing a new token of a kind retires the outstanding ones, so an old link stops working. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
                    UPDATE user_action_token
                       SET consumed_at = :consumedAt
                     WHERE user_id = :userId
                       AND token_type = :tokenType
                       AND consumed_at IS NULL
                    """, nativeQuery = true)
    int consumeOutstanding(
            @Param("userId") UUID userId,
            @Param("tokenType") String tokenType,
            @Param("consumedAt") Instant consumedAt);
}
