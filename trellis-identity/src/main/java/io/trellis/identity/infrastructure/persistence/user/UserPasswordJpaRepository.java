package io.trellis.identity.infrastructure.persistence.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Native SQL over {@code user_password} (ADR 0004). */
public interface UserPasswordJpaRepository extends JpaRepository<UserPasswordEntity, Long> {

  @Query(value = "SELECT p.* FROM user_password p WHERE p.user_id = :userId", nativeQuery = true)
  Optional<UserPasswordEntity> findByUserId(@Param("userId") Long userId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
                    UPDATE user_password
                       SET password_hash = :passwordHash,
                           password_changed_at = :changedAt
                     WHERE user_id = :userId
                    """,
      nativeQuery = true)
  int changePassword(
      @Param("userId") Long userId,
      @Param("passwordHash") String passwordHash,
      @Param("changedAt") Instant changedAt);
}
