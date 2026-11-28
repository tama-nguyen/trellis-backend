package io.trellis.identity.infrastructure.persistence.user;

import io.trellis.identity.domain.user.NewUser;
import io.trellis.identity.domain.user.User;

/**
 * Converts between the domain's immutable records and the mutable entities JPA needs.
 *
 * <p>The two shapes exist for different reasons and must not be merged: a record cannot carry
 * {@code @Entity}, and an entity in {@code domain} would put Hibernate inside the business rules,
 * which {@code businessDomainIsFrameworkFree} forbids.
 */
final class UserEntities {

  private UserEntities() {}

  static User toDomain(UserEntity entity) {
    return new User(
        entity.getId(),
        entity.getEmail(),
        entity.getDisplayName(),
        entity.getStatus(),
        entity.getEmailVerifiedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  static UserEntity toEntity(NewUser user) {
    UserEntity entity = new UserEntity();
    entity.setEmail(user.email());
    entity.setDisplayName(user.displayName());
    entity.setStatus(user.status());
    entity.setEmailVerifiedAt(user.emailVerifiedAt());
    entity.setCreatedAt(user.createdAt());
    entity.setUpdatedAt(user.updatedAt());
    return entity;
  }
}
