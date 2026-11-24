package io.trellis.identity.application.usecase.authentication;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.api.security.IdentityAuditActions;
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

  private final UserRepository userRepository;
  private final TokenCodec tokenCodec;
  private final IdentityProperties identityProperties;

  @SecurityAudited(SecurityAction.REFRESH)
  @Audited(
      action = IdentityAuditActions.REFRESH,
      aggregateType = IdentityAuditActions.AGGREGATE_TYPE)
  @Transactional
  @Observed(name = "identity.refresh-session")
  public AuthTokens execute(final RequestContext ctx, final String rawRefreshToken) {
    if (!tokenCodec.isType(rawRefreshToken, TokenType.REFRESH)) {
      throw IdentityErrors.refreshTokenInvalid();
    }

    final Instant requestTime = ctx.requestTime();
    final String refreshTokenHash = tokenCodec.hash(rawRefreshToken);
    final UserSession refreshSession =
        userRepository
            .lockActiveRefreshSession(refreshTokenHash, requestTime)
            .orElseThrow(IdentityErrors::refreshTokenInvalid);
    SignInGuard.require(refreshSession.user());

    final SecretToken accessToken = tokenCodec.issue(TokenType.ACCESS);
    final SecretToken refreshToken = tokenCodec.issue(TokenType.REFRESH);

    userRepository.rotateSession(
        refreshSession.id(),
        accessToken.hash(),
        refreshToken.hash(),
        requestTime.plus(identityProperties.accessTokenTtl()),
        refreshSession.refreshExpiresAt(),
        requestTime);

    return new AuthTokens(accessToken.raw(), refreshToken.raw());
  }
}
