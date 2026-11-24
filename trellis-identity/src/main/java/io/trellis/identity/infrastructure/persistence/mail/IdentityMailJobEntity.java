package io.trellis.identity.infrastructure.persistence.mail;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "identity_mail_job")
@Getter
@Setter
@NoArgsConstructor
public class IdentityMailJobEntity {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private IdentityMailJobStatus status = IdentityMailJobStatus.PENDING;

  @Column(name = "action_hash", nullable = false, length = 64)
  private String actionHash;

  @Column(name = "key_version", nullable = false, length = 32)
  private String keyVersion;

  @Column(name = "payload", nullable = false)
  private String payload;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "lease_id")
  private UUID leaseId;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
