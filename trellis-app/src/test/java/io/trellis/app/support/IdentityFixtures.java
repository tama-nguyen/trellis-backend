package io.trellis.app.support;

import io.trellis.common.context.RequestContext;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
public class IdentityFixtures {
  public static final String PASSWORD = "Valid-test-password-42!";
  private final UserRepository users;
  private final PasswordHasher passwords;
  private final PlatformTransactionManager manager;
  private final Clock clock;
  private final TokenCodec tokens;
  private final IdentityProperties properties;

  public UUID nativeUser(final UserStatus status) {
    val id = UUID.randomUUID();
    val now = clock.instant();
    tx().executeWithoutResult(
            tx ->
                users.createUser(
                    new User(
                        id,
                        email(id),
                        "Native user",
                        status,
                        status == UserStatus.ACTIVE ? now : null,
                        now,
                        now),
                    passwords.hash(PASSWORD)));
    return id;
  }

  public NativeSession session(final UserStatus status) {
    val userId = nativeUser(status);
    val access = tokens.issue(TokenType.ACCESS);
    val refresh = tokens.issue(TokenType.REFRESH);
    val now = clock.instant();
    val accessExpiry = now.plus(properties.accessTokenTtl());
    tx().executeWithoutResult(
            tx ->
                users.createSession(
                    UUID.randomUUID(),
                    userId,
                    access.hash(),
                    refresh.hash(),
                    accessExpiry,
                    now.plus(properties.refreshTokenTtl()),
                    now));
    return new NativeSession(userId, access, refresh, accessExpiry);
  }

  public void revoke(final NativeSession session) {
    tx().executeWithoutResult(tx -> users.revokeSessions(session.userId(), clock.instant()));
  }

  public record NativeSession(
      UUID userId,
      TokenCodec.SecretToken access,
      TokenCodec.SecretToken refresh,
      Instant accessExpiry) {}

  public TransactionTemplate tx() {
    return new TransactionTemplate(manager);
  }

  public static String email(final UUID id) {
    return id + "@example.test";
  }

  public static RequestContext context() {
    return RequestContext.builder()
        .correlationId("test-" + UUID.randomUUID())
        .requestTime(Instant.now())
        .build();
  }
}
