package io.trellis.identity.application.port;

import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.session.UserSession;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.NewUser;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserCredentials;
import java.time.Instant;
import java.util.Optional;

/** Persistence boundary for native users, sessions and one-time user actions. */
public interface UserRepository {
  User createUser(NewUser user, String passwordHash);

  Optional<UserCredentials> credentialsByEmail(EmailAddress email);

  Optional<User> userByEmail(EmailAddress email);

  Optional<User> userById(Long id);

  void createSession(
      Long userId,
      String accessTokenHash,
      String refreshTokenHash,
      Instant accessExpiresAt,
      Instant refreshExpiresAt,
      Instant createdAt);

  Optional<User> userByActiveAccessToken(String tokenHash, Instant now);

  Optional<UserSession> lockActiveRefreshSession(String tokenHash, Instant now);

  void rotateSession(
      Long id,
      String accessTokenHash,
      String refreshTokenHash,
      Instant accessExpiresAt,
      Instant refreshExpiresAt,
      Instant rotatedAt);

  void revokeSessionByRefreshToken(String tokenHash, Instant revokedAt);

  void revokeSessions(Long userId, Instant revokedAt);

  void createActionToken(
      Long userId, UserActionType type, String tokenHash, Instant expiresAt, Instant createdAt);

  Optional<UserActionToken> lockActiveActionToken(
      String tokenHash, UserActionType type, Instant now);

  void consumeActionToken(Long id, Instant consumedAt);

  void verifyEmail(Long userId, Instant verifiedAt);

  void changePassword(Long userId, String passwordHash, Instant changedAt);
}
