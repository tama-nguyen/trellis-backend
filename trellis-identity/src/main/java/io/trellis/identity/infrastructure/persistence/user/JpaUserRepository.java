package io.trellis.identity.infrastructure.persistence.user;

import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.session.UserSession;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserCredentials;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenEntity;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenJpaRepository;
import io.trellis.identity.infrastructure.persistence.session.UserSessionEntity;
import io.trellis.identity.infrastructure.persistence.session.UserSessionJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the module's persistence port over Spring Data JPA.
 *
 * <p>Its job is translation only: the application layer asks in domain terms, and each method here
 * is one repository call plus a mapping. Anything that needed a hand-written statement - the two
 * row locks, the guarded updates - is a {@code @Query} on the Spring Data interfaces next to this
 * class.
 *
 * <p>Inserts go through {@link EntityManager#persist} rather than {@code save()}. Every id here is
 * assigned before the row exists, and Spring Data reads a non-null id with no {@code @Version} as
 * "already persisted", so {@code save()} would merge: one SELECT per insert, and update semantics
 * for a row that is new.
 */
@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {

  @PersistenceContext private EntityManager entityManager;

  private final UserJpaRepository users;
  private final UserPasswordJpaRepository passwords;
  private final UserSessionJpaRepository sessions;
  private final UserActionTokenJpaRepository actionTokens;

  @Override
  public void createUser(User user, String passwordHash) {
    entityManager.persist(UserEntities.toEntity(user));
    UserPasswordEntity password = new UserPasswordEntity();
    password.setUserId(user.id());
    password.setPasswordHash(passwordHash);
    password.setPasswordChangedAt(user.createdAt());
    entityManager.persist(password);
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
  public Optional<User> userById(UUID id) {
    return users.findById(id).map(UserEntities::toDomain);
  }

  @Override
  public void createSession(
      UUID id,
      UUID userId,
      String accessTokenHash,
      String refreshTokenHash,
      Instant accessExpiresAt,
      Instant refreshExpiresAt,
      Instant createdAt) {
    UserSessionEntity session = new UserSessionEntity();
    session.setId(id);
    session.setUserId(userId);
    session.setAccessTokenHash(accessTokenHash);
    session.setRefreshTokenHash(refreshTokenHash);
    session.setAccessExpiresAt(accessExpiresAt);
    session.setRefreshExpiresAt(refreshExpiresAt);
    session.setCreatedAt(createdAt);
    entityManager.persist(session);
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
      UUID id,
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
  public void revokeSessions(UUID userId, Instant revokedAt) {
    sessions.revokeAllForUser(userId, revokedAt);
  }

  @Override
  public void createActionToken(
      UUID id,
      UUID userId,
      UserActionType type,
      String tokenHash,
      Instant expiresAt,
      Instant createdAt) {
    actionTokens.consumeOutstanding(userId, type.name(), createdAt);
    UserActionTokenEntity token = new UserActionTokenEntity();
    token.setId(id);
    token.setUserId(userId);
    token.setTokenType(type);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(expiresAt);
    token.setCreatedAt(createdAt);
    entityManager.persist(token);
  }

  @Override
  public Optional<UserActionToken> lockActiveActionToken(
      String tokenHash, UserActionType type, Instant now) {
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

  @Override
  public void consumeActionToken(UUID id, Instant consumedAt) {
    actionTokens.consume(id, consumedAt);
  }

  @Override
  public void verifyEmail(UUID userId, Instant verifiedAt) {
    users.markEmailVerified(
        userId, verifiedAt, UserStatus.ACTIVE.name(), UserStatus.PENDING_VERIFICATION.name());
  }

  @Override
  public void changePassword(UUID userId, String passwordHash, Instant changedAt) {
    passwords.changePassword(userId, passwordHash, changedAt);
  }
}
