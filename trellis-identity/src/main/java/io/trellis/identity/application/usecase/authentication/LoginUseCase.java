package io.trellis.identity.application.usecase.authentication;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.UuidV7;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.api.security.IdentityAuditActions;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.SignInGuard;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserCredentials;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exchanges an email and password for a session. */
@Service
public class LoginUseCase {

  /**
   * Verified when no user matches, so a wrong email costs the same time as a wrong password.
   * Without it, response timing tells an attacker which addresses are registered.
   */
  private static final String ABSENT_USER_PASSWORD = "not-a-real-user-password";

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final TokenCodec tokenCodec;
  private final IdentityProperties identityProperties;
  private final Clock clock;
  private final String absentUserPasswordHash;

  public LoginUseCase(
      final UserRepository userRepository,
      final PasswordHasher passwordHasher,
      final TokenCodec tokenCodec,
      final IdentityProperties identityProperties,
      final Clock clock) {
    this.userRepository = userRepository;
    this.passwordHasher = passwordHasher;
    this.tokenCodec = tokenCodec;
    this.identityProperties = identityProperties;
    this.clock = clock;
    this.absentUserPasswordHash = passwordHasher.hash(ABSENT_USER_PASSWORD);
  }

  @SecurityAudited(SecurityAction.LOGIN)
  @Audited(action = IdentityAuditActions.LOGIN, aggregateType = IdentityAuditActions.AGGREGATE_TYPE)
  @Transactional
  @Observed(name = "identity.sign-in")
  public AuthTokens execute(final RequestContext ctx, final String email, final String password) {
    final Optional<UserCredentials> userCredentials =
        userRepository.credentialsByEmail(EmailAddress.of(email));
    final String passwordHash =
        userCredentials.map(UserCredentials::passwordHash).orElse(absentUserPasswordHash);
    if (!passwordHasher.matches(password, passwordHash) || userCredentials.isEmpty()) {
      throw IdentityErrors.invalidCredentials();
    }

    final User signingInUser = userCredentials.orElseThrow().user();
    SignInGuard.require(signingInUser);

    return openSession(signingInUser, ctx.requestTime());
  }

  private AuthTokens openSession(final User signingInUser, final Instant requestTime) {
    final SecretToken accessToken = tokenCodec.issue(TokenType.ACCESS);
    final SecretToken refreshToken = tokenCodec.issue(TokenType.REFRESH);

    userRepository.createSession(
        UuidV7.next(clock),
        signingInUser.id(),
        accessToken.hash(),
        refreshToken.hash(),
        requestTime.plus(identityProperties.accessTokenTtl()),
        requestTime.plus(identityProperties.refreshTokenTtl()),
        requestTime);

    return new AuthTokens(accessToken.raw(), refreshToken.raw());
  }
}
