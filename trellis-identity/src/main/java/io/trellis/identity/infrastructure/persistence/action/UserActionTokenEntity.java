package io.trellis.identity.infrastructure.persistence.action;

import io.trellis.identity.domain.action.UserActionType;
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

/** Row of {@code user_action_token}: one single-use email verification or password reset secret. */
@Entity
@Table(name = "user_action_token")
@Getter
@Setter
@NoArgsConstructor
public class UserActionTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_action_token_ids")
  @SequenceGenerator(
      name = "user_action_token_ids",
      sequenceName = "user_action_token_id_seq",
      allocationSize = 1)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "token_type", nullable = false)
  private UserActionType tokenType;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;
}
