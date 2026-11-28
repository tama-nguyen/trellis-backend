package io.trellis.identity.infrastructure.persistence.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * Row of {@code user_password}. A separate aggregate from the user on purpose: reading an user must
 * never drag the password hash along with it.
 */
@Entity
@Table(name = "user_password")
@Getter
@Setter
@NoArgsConstructor
public class UserPasswordEntity implements Persistable<Long> {
  @Transient private boolean newEntity = true;

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt;

  @Override
  public Long getId() {
    return userId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  private void markPersisted() {
    newEntity = false;
  }
}
