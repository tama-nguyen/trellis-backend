package io.trellis.identity.infrastructure.persistence.user;

import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.session.UserSession;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.NewUser;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserCredentials;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenEntity;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenJpaRepository;
import io.trellis.identity.infrastructure.persistence.session.UserSessionEntity;
import io.trellis.identity.infrastructure.persistence.session.UserSessionJpaRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Implements the module's persistence port over Spring Data JPA.
 *
 * <p>Its job is translation only: the application layer asks in domain terms, and each method here
 * is one repository call plus a mapping. Anything that needed a hand-written statement - the two
 * row locks, the guarded updates - is a {@code @Query} on the Spring Data interfaces next to this
 * class.
 *
 * <p>New entities use repository save with generated Long IDs. Explicit flushes preserve
 * duplicate-email error translation and action issuance locking.
 */
@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {
  private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

  private final UserJpaRepository users;
  private final UserPasswordJpaRepository passwords;
  private final UserSessionJpaRepository sessions;
  private final UserActionTokenJpaRepository actionTokens;

  @Override
  public User createUser(final NewUser user, final String passwordHash) {
    try {
      final UserEntity saved = users.saveAndFlush(UserEntities.toEntity(user));
      passwords.saveAndFlush(
          UserEntities.toPasswordEntity(saved.getId(), passwordHash, user.createdAt()));
      return UserEntities.toDomain(saved);
    } catch (final RuntimeException failure) {
      if (isDuplicateEmail(failure)) {
        throw new DuplicateKeyException("Email is already registered");
      }
      throw failure;
    }
  }

  @Override
  public Optional<UserCredentials> credentialsByEmail(final EmailAddress email) {
    return users.findByEmail(email.value()).flatMap(this::toCredentials);
  }

  @Override
  public Optional<User> userByEmail(final EmailAddress email) {
    return users.findByEmail(email.value()).map(UserEntities::toDomain);
  }

  @Override
  public Optional<User> userById(final Long id) {
    return users.findById(id).map(UserEntities::toDomain);
  }

  @Override
  public void createSession(
      final Long userId,
      final String accessTokenHash,
      final String refreshTokenHash,
      final Instant accessExpiresAt,
      final Instant refreshExpiresAt,
      final Instant createdAt) {
    sessions.save(
        UserEntities.toSessionEntity(
            userId,
            accessTokenHash,
            refreshTokenHash,
            accessExpiresAt,
            refreshExpiresAt,
            createdAt));
  }

  @Override
  public Optional<User> userByActiveAccessToken(final String tokenHash, final Instant now) {
    return users.findByActiveAccessToken(tokenHash, now).map(UserEntities::toDomain);
  }

  @Override
  public Optional<UserSession> lockActiveRefreshSession(final String tokenHash, final Instant now) {
    return sessions.lockActiveByRefreshToken(tokenHash, now).flatMap(this::toUserSession);
  }

  @Override
  public void rotateSession(
      final Long id,
      final String accessTokenHash,
      final String refreshTokenHash,
      final Instant accessExpiresAt,
      final Instant refreshExpiresAt,
      final Instant rotatedAt) {
    sessions.rotate(
        id, accessTokenHash, refreshTokenHash, accessExpiresAt, refreshExpiresAt, rotatedAt);
  }

  @Override
  public void revokeSessionByRefreshToken(final String tokenHash, final Instant revokedAt) {
    sessions.revokeByRefreshToken(tokenHash, revokedAt);
  }

  @Override
  public void revokeSessions(final Long userId, final Instant revokedAt) {
    sessions.revokeAllForUser(userId, revokedAt);
  }

  @Override
  public void createActionToken(
      final Long userId,
      final UserActionType type,
      final String tokenHash,
      final Instant expiresAt,
      final Instant createdAt) {
    lockUser(userId);
    actionTokens.consumeOutstanding(userId, type.name(), createdAt);
    actionTokens.save(
        UserEntities.toActionTokenEntity(userId, type, tokenHash, expiresAt, createdAt));
  }

  @Override
  public Optional<UserActionToken> lockActiveActionToken(
      final String tokenHash, final UserActionType type, final Instant now) {
    final Optional<Long> userId = actionTokens.userIdByHash(tokenHash, type.name());
    if (userId.isEmpty()) {
      return Optional.empty();
    }
    // The user row is locked before the token row, the same order as issuance.
    lockUser(userId.orElseThrow());

    return actionTokens.lockActive(tokenHash, type.name(), now).flatMap(this::toActionToken);
  }

  @Override
  public void consumeActionToken(final Long id, final Instant consumedAt) {
    actionTokens.consume(id, consumedAt);
  }

  @Override
  public void verifyEmail(final Long userId, final Instant verifiedAt) {
    users.markEmailVerified(
        userId, verifiedAt, UserStatus.ACTIVE.name(), UserStatus.PENDING_VERIFICATION.name());
  }

  @Override
  public void changePassword(
      final Long userId, final String passwordHash, final Instant changedAt) {
    passwords.changePassword(userId, passwordHash, changedAt);
  }

  private Optional<UserCredentials> toCredentials(final UserEntity user) {
    return passwords
        .findByUserId(user.getId())
        .map(
            password ->
                new UserCredentials(UserEntities.toDomain(user), password.getPasswordHash()));
  }

  private Optional<UserSession> toUserSession(final UserSessionEntity session) {
    return userById(session.getUserId())
        .map(user -> new UserSession(session.getId(), user, session.getRefreshExpiresAt()));
  }

  private Optional<UserActionToken> toActionToken(final UserActionTokenEntity token) {
    return userById(token.getUserId())
        .map(user -> new UserActionToken(token.getId(), user, token.getTokenType()));
  }

  private void lockUser(final Long id) {
    // Flush a newly registered user before taking the shared issuance/consumption lock.
    users.flush();
    users.lockById(id).orElseThrow(() -> new IllegalStateException("Action user does not exist"));
  }

  private static boolean isDuplicateEmail(final Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraint
          && EMAIL_UNIQUE_CONSTRAINT.equals(constraint.getConstraintName())) {
        return true;
      }
    }
    return false;
  }
}
