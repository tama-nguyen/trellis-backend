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

  private final UserJpaRepository users;
  private final UserPasswordJpaRepository passwords;
  private final UserSessionJpaRepository sessions;
  private final UserActionTokenJpaRepository actionTokens;

  @Override
  public User createUser(final NewUser user, final String passwordHash) {
    try {
      final UserEntity saved = users.saveAndFlush(UserEntities.toEntity(user));
      final UserPasswordEntity password = new UserPasswordEntity();
      password.setUserId(saved.getId());
      password.setPasswordHash(passwordHash);
      password.setPasswordChangedAt(user.createdAt());
      passwords.saveAndFlush(password);
      return UserEntities.toDomain(saved);
    } catch (RuntimeException failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException constraint
            && "uq_users_email".equals(constraint.getConstraintName())) {
          throw new DuplicateKeyException("Email is already registered");
        }
      }
      throw failure;
    }
  }

  @Override
  public Optional<UserCredentials> credentialsByEmail(EmailAddress email) {
    return users
        .findByEmail(email.value())
        .flatMap(
            user ->
                passwords
                    .findByUserId(user.getId())
                    .map(
                        password ->
                            new UserCredentials(
                                UserEntities.toDomain(user), password.getPasswordHash())));
  }

  @Override
  public Optional<User> userByEmail(EmailAddress email) {
    return users.findByEmail(email.value()).map(UserEntities::toDomain);
  }

  @Override
  public Optional<User> userById(Long id) {
    return users.findById(id).map(UserEntities::toDomain);
  }

  @Override
  public void createSession(
      Long userId,
      String accessTokenHash,
      String refreshTokenHash,
      Instant accessExpiresAt,
      Instant refreshExpiresAt,
      Instant createdAt) {
    UserSessionEntity session = new UserSessionEntity();
    session.setUserId(userId);
    session.setAccessTokenHash(accessTokenHash);
    session.setRefreshTokenHash(refreshTokenHash);
    session.setAccessExpiresAt(accessExpiresAt);
    session.setRefreshExpiresAt(refreshExpiresAt);
    session.setCreatedAt(createdAt);
    sessions.save(session);
  }

  @Override
  public Optional<User> userByActiveAccessToken(String tokenHash, Instant now) {
    return users.findByActiveAccessToken(tokenHash, now).map(UserEntities::toDomain);
  }

  @Override
  public Optional<UserSession> lockActiveRefreshSession(String tokenHash, Instant now) {
    return sessions
        .lockActiveByRefreshToken(tokenHash, now)
        .flatMap(
            session ->
                users
                    .findById(session.getUserId())
                    .map(
                        user ->
                            new UserSession(
                                session.getId(),
                                UserEntities.toDomain(user),
                                session.getRefreshExpiresAt())));
  }

  @Override
  public void rotateSession(
      Long id,
      String accessTokenHash,
      String refreshTokenHash,
      Instant accessExpiresAt,
      Instant refreshExpiresAt,
      Instant rotatedAt) {
    sessions.rotate(
        id, accessTokenHash, refreshTokenHash, accessExpiresAt, refreshExpiresAt, rotatedAt);
  }

  @Override
  public void revokeSessionByRefreshToken(String tokenHash, Instant revokedAt) {
    sessions.revokeByRefreshToken(tokenHash, revokedAt);
  }

  @Override
  public void revokeSessions(Long userId, Instant revokedAt) {
    sessions.revokeAllForUser(userId, revokedAt);
  }

  @Override
  public void createActionToken(
      Long userId, UserActionType type, String tokenHash, Instant expiresAt, Instant createdAt) {
    lockUser(userId);
    actionTokens.consumeOutstanding(userId, type.name(), createdAt);
    UserActionTokenEntity token = new UserActionTokenEntity();
    token.setUserId(userId);
    token.setTokenType(type);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(expiresAt);
    token.setCreatedAt(createdAt);
    actionTokens.save(token);
  }

  @Override
  public Optional<UserActionToken> lockActiveActionToken(
      String tokenHash, UserActionType type, Instant now) {
    Optional<Long> userId = actionTokens.userIdByHash(tokenHash, type.name());
    if (userId.isEmpty()) return Optional.empty();
    lockUser(userId.orElseThrow());
    return actionTokens
        .lockActive(tokenHash, type.name(), now)
        .flatMap(
            token ->
                users
                    .findById(token.getUserId())
                    .map(
                        user ->
                            new UserActionToken(
                                token.getId(), UserEntities.toDomain(user), token.getTokenType())));
  }

  private void lockUser(Long id) {
    // Flush a newly registered user before taking the shared issuance/consumption lock.
    users.flush();
    users.lockById(id).orElseThrow(() -> new IllegalStateException("Action user does not exist"));
  }

  @Override
  public void consumeActionToken(Long id, Instant consumedAt) {
    actionTokens.consume(id, consumedAt);
  }

  @Override
  public void verifyEmail(Long userId, Instant verifiedAt) {
    users.markEmailVerified(
        userId, verifiedAt, UserStatus.ACTIVE.name(), UserStatus.PENDING_VERIFICATION.name());
  }

  @Override
  public void changePassword(Long userId, String passwordHash, Instant changedAt) {
    passwords.changePassword(userId, passwordHash, changedAt);
  }
}
