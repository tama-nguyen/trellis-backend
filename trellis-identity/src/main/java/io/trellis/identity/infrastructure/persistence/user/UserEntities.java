package io.trellis.identity.infrastructure.persistence.user;

import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.NewUser;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenEntity;
import io.trellis.identity.infrastructure.persistence.session.UserSessionEntity;
import java.time.Instant;

/**
 * Converts between the domain's immutable records and the mutable entities JPA needs.
 *
 * <p>The two shapes exist for different reasons and must not be merged: a record cannot carry
 * {@code @Entity}, and an entity in {@code domain} would put Hibernate inside the business rules,
 * which {@code businessDomainIsFrameworkFree} forbids.
 */
final class UserEntities {

  private UserEntities() {}

  static User toDomain(final UserEntity entity) {
    return new User(
        entity.getId(),
        entity.getEmail(),
        entity.getDisplayName(),
        entity.getStatus(),
        entity.getEmailVerifiedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  static UserEntity toEntity(final NewUser user) {
    final UserEntity entity = new UserEntity();
    entity.setEmail(user.email());
    entity.setDisplayName(user.displayName());
    entity.setStatus(user.status());
    entity.setEmailVerifiedAt(user.emailVerifiedAt());
    entity.setCreatedAt(user.createdAt());
    entity.setUpdatedAt(user.updatedAt());
    return entity;
  }

  static UserPasswordEntity toPasswordEntity(
      final Long userId, final String passwordHash, final Instant changedAt) {
    final UserPasswordEntity entity = new UserPasswordEntity();
    entity.setUserId(userId);
    entity.setPasswordHash(passwordHash);
    entity.setPasswordChangedAt(changedAt);
    return entity;
  }

  static UserSessionEntity toSessionEntity(
      final Long userId,
      final String accessTokenHash,
      final String refreshTokenHash,
      final Instant accessExpiresAt,
      final Instant refreshExpiresAt,
      final Instant createdAt) {
    final UserSessionEntity entity = new UserSessionEntity();
    entity.setUserId(userId);
    entity.setAccessTokenHash(accessTokenHash);
    entity.setRefreshTokenHash(refreshTokenHash);
    entity.setAccessExpiresAt(accessExpiresAt);
    entity.setRefreshExpiresAt(refreshExpiresAt);
    entity.setCreatedAt(createdAt);
    return entity;
  }

  static UserActionTokenEntity toActionTokenEntity(
      final Long userId,
      final UserActionType type,
      final String tokenHash,
      final Instant expiresAt,
      final Instant createdAt) {
    final UserActionTokenEntity entity = new UserActionTokenEntity();
    entity.setUserId(userId);
    entity.setTokenType(type);
    entity.setTokenHash(tokenHash);
    entity.setExpiresAt(expiresAt);
    entity.setCreatedAt(createdAt);
    return entity;
  }
}
