package io.trellis.identity.application.usecase.authentication;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.context.RequestContext;
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

  private final UserRepository users;
  private final TokenCodec tokens;

  @Transactional
  @Observed(name = "identity.sign-out")
  public void execute(RequestContext ctx, String rawRefreshToken) {
    if (tokens.isType(rawRefreshToken, TokenType.REFRESH)) {
      users.revokeSessionByRefreshToken(tokens.hash(rawRefreshToken), ctx.requestTime());
    }
  }
}
