package io.trellis.identity.application.usecase.authentication;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.api.security.IdentityAuditActions;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.token.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends one session. A token of the wrong kind, or an unknown one, is silently nothing to revoke.
 */
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

  private final UserRepository userRepository;
  private final TokenCodec tokenCodec;

  @SecurityAudited(SecurityAction.LOGOUT)
  @Audited(
      action = IdentityAuditActions.LOGOUT,
      aggregateType = IdentityAuditActions.AGGREGATE_TYPE)
  @Transactional
  @Observed(name = "identity.sign-out")
  public void execute(final RequestContext ctx, final String rawRefreshToken) {
    if (!tokenCodec.isType(rawRefreshToken, TokenType.REFRESH)) {
      return;
    }

    final String refreshTokenHash = tokenCodec.hash(rawRefreshToken);
    userRepository.revokeSessionByRefreshToken(refreshTokenHash, ctx.requestTime());
  }
}
