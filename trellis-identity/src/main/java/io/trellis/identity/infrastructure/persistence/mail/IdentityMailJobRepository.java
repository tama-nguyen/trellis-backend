package io.trellis.identity.infrastructure.persistence.mail;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityMailJobRepository extends JpaRepository<IdentityMailJobEntity, Long> {
  @Query(
      value =
          """
        SELECT j.*
          FROM identity_mail_job j
         WHERE j.completed_at IS NULL
           AND j.status = 'PENDING'
           AND j.next_attempt_at <= :now
           AND (j.lease_until IS NULL OR j.lease_until <= :now)
           AND j.attempts < :attempts
           AND EXISTS (
               SELECT 1 FROM user_action_token a
                WHERE a.token_hash = j.action_hash
                  AND a.consumed_at IS NULL AND a.expires_at > :now
           )
         ORDER BY j.next_attempt_at, j.id
         LIMIT 1 FOR UPDATE OF j SKIP LOCKED
        """,
      nativeQuery = true)
  Optional<IdentityMailJobEntity> claimable(
      @Param("now") Instant now, @Param("attempts") int attempts);

  @Modifying
  @Query(
      value =
          """
        UPDATE identity_mail_job
           SET completed_at = :now, status = 'SENT', payload = ''
         WHERE id = :id AND lease_id = :lease AND completed_at IS NULL AND status = 'PENDING'
        """,
      nativeQuery = true)
  int complete(@Param("id") Long id, @Param("lease") UUID lease, @Param("now") Instant now);

  @Modifying
  @Query(
      value =
          """
        UPDATE identity_mail_job j
           SET completed_at = :now,
               status = CASE WHEN EXISTS (
                   SELECT 1 FROM user_action_token a
                    WHERE a.token_hash = j.action_hash
                      AND a.consumed_at IS NULL AND a.expires_at > :now
               ) THEN 'FAILED' ELSE 'CANCELLED' END,
               payload = CASE WHEN EXISTS (
                   SELECT 1 FROM user_action_token a
                    WHERE a.token_hash = j.action_hash
                      AND a.consumed_at IS NULL AND a.expires_at > :now
               ) THEN j.payload ELSE '' END
         WHERE j.completed_at IS NULL AND j.status = 'PENDING'
           AND (j.attempts >= :attempts OR NOT EXISTS (
               SELECT 1 FROM user_action_token a
                WHERE a.token_hash = j.action_hash
                  AND a.consumed_at IS NULL AND a.expires_at > :now
           ))
           AND (j.lease_until IS NULL OR j.lease_until <= :now)
        """,
      nativeQuery = true)
  int retireInvalid(@Param("now") Instant now, @Param("attempts") int attempts);

  @Modifying
  @Query(
      value =
          """
        UPDATE identity_mail_job j SET payload = ''
         WHERE j.completed_at IS NOT NULL AND j.payload <> ''
           AND NOT EXISTS (
               SELECT 1 FROM user_action_token a
                WHERE a.token_hash = j.action_hash
                  AND a.consumed_at IS NULL AND a.expires_at > :now
           )
        """,
      nativeQuery = true)
  int clearInvalidTerminalPayloads(@Param("now") Instant now);

  @Modifying
  @Query(value = "DELETE FROM identity_mail_job WHERE completed_at < :before", nativeQuery = true)
  int deleteCompleted(@Param("before") Instant before);
}
