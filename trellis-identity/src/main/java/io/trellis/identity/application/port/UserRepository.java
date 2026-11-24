package io.trellis.identity.application.port;

import io.trellis.identity.domain.model.User;
import io.trellis.identity.domain.model.UserActionToken;
import io.trellis.identity.domain.model.UserActionType;
import io.trellis.identity.domain.model.UserCredentials;
import io.trellis.identity.domain.model.UserSession;
import io.trellis.identity.domain.value.EmailAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for native users, sessions and one-time user actions. */
public interface UserRepository {
    void createUser(User user, String passwordHash);

    Optional<UserCredentials> credentialsByEmail(EmailAddress email);

    Optional<User> userByEmail(EmailAddress email);

    Optional<User> userById(UUID id);

    void createSession(
            UUID id,
            UUID userId,
            String accessTokenHash,
            String refreshTokenHash,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            Instant createdAt);

    Optional<User> userByActiveAccessToken(String tokenHash, Instant now);

    Optional<UserSession> lockActiveRefreshSession(String tokenHash, Instant now);

    void rotateSession(
            UUID id,
            String accessTokenHash,
            String refreshTokenHash,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            Instant rotatedAt);

    void revokeSessionByRefreshToken(String tokenHash, Instant revokedAt);

    void revokeSessions(UUID userId, Instant revokedAt);

    void createActionToken(
            UUID id, UUID userId, UserActionType type, String tokenHash, Instant expiresAt, Instant createdAt);

    Optional<UserActionToken> lockActiveActionToken(String tokenHash, UserActionType type, Instant now);

    void consumeActionToken(UUID id, Instant consumedAt);

    void verifyEmail(UUID userId, Instant verifiedAt);

    void changePassword(UUID userId, String passwordHash, Instant changedAt);
}
