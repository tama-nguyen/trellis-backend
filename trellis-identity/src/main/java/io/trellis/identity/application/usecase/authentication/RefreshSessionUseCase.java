package io.trellis.identity.application.usecase.authentication;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.SignInGuard;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.domain.session.UserSession;
import io.trellis.identity.domain.token.TokenType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotates a refresh token.
 *
 * <p>The refresh window does not move: a session has an absolute lifetime, and rotating inside it
 * must not extend it, or a stolen token could be refreshed forever.
 */
@Service
@RequiredArgsConstructor
public class RefreshSessionUseCase {

  private final UserRepository users;
  private final TokenCodec tokens;
  private final IdentityProperties properties;

  @Transactional
  @Observed(name = "identity.refresh-session")
  public AuthTokens execute(RequestContext ctx, String rawRefreshToken) {
    if (!tokens.isType(rawRefreshToken, TokenType.REFRESH)) {
      throw IdentityErrors.refreshTokenInvalid();
    }
    Instant now = ctx.requestTime();
    UserSession session =
        users
            .lockActiveRefreshSession(tokens.hash(rawRefreshToken), now)
            .orElseThrow(IdentityErrors::refreshTokenInvalid);
    SignInGuard.require(session.user());
    SecretToken access = tokens.issue(TokenType.ACCESS);
    SecretToken refresh = tokens.issue(TokenType.REFRESH);
    users.rotateSession(
        session.id(),
        access.hash(),
        refresh.hash(),
        now.plus(properties.accessTokenTtl()),
        session.refreshExpiresAt(),
        now);
    return new AuthTokens(access.raw(), refresh.raw());
  }
}
