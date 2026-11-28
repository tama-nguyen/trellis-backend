package io.trellis.identity.infrastructure.persistence.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row of {@code user_session}. {@code userId} is a plain column, not a {@code @ManyToOne}: sessions
 * and users are separate aggregates, and the schema carries no foreign key (ADR 0004).
 */
@Entity
@Table(name = "user_session")
@Getter
@Setter
@NoArgsConstructor
public class UserSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_session_ids")
  @SequenceGenerator(
      name = "user_session_ids",
      sequenceName = "user_session_id_seq",
      allocationSize = 1)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "access_token_hash", nullable = false, length = 64)
  private String accessTokenHash;

  @Column(name = "refresh_token_hash", nullable = false, length = 64)
  private String refreshTokenHash;

  @Column(name = "access_expires_at", nullable = false)
  private Instant accessExpiresAt;

  @Column(name = "refresh_expires_at", nullable = false)
  private Instant refreshExpiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;
}
