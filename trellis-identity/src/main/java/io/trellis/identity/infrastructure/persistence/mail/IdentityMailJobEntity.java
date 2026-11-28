package io.trellis.identity.infrastructure.persistence.mail;

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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "identity_mail_job")
@Getter
@Setter
@NoArgsConstructor
public class IdentityMailJobEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "identity_mail_job_ids")
  @SequenceGenerator(
      name = "identity_mail_job_ids",
      sequenceName = "identity_mail_job_id_seq",
      allocationSize = 1)
  @Column(name = "id", nullable = false)
  private Long id;

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
