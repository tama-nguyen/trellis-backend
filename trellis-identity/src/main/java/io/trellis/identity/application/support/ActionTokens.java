package io.trellis.identity.application.support;

import io.trellis.common.id.UuidV7;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.token.TokenType;
import io.trellis.identity.domain.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The two things every single-use link does: hand one out, and take one back.
 *
 * <p>It is a collaborator rather than a use case of its own - nothing outside this module asks for
 * an action token. It exists so that four use cases share one definition of what a valid token is,
 * instead of four copies drifting apart.
 */
@Service
@RequiredArgsConstructor
public class ActionTokens {

  private final UserRepository userRepository;
  private final TokenCodec tokenCodec;
  private final Clock clock;

  /**
   * Issues a token of {@code type}; the caller mails the raw value and stores nothing but its hash.
   */
  public SecretToken issue(
      final User user, final UserActionType type, final Duration ttl, final Instant now) {
    final SecretToken actionToken = tokenCodec.issue(TokenType.ACTION);
    userRepository.createActionToken(
        UuidV7.next(clock), user.id(), type, actionToken.hash(), now.plus(ttl), now);
    return actionToken;
  }

  /**
   * Locks the row behind a raw token so it can be consumed exactly once.
   *
   * @throws io.trellis.common.error.TrellisException when the token is of the wrong kind, unknown,
   *     expired or already used - all four answer the same way, so a caller learns nothing from
   *     which it was
   */
  public UserActionToken lockActive(
      final String rawToken, final UserActionType type, final Instant now) {
    if (!tokenCodec.isType(rawToken, TokenType.ACTION)) {
      throw IdentityErrors.actionTokenInvalid();
    }
    return userRepository
        .lockActiveActionToken(tokenCodec.hash(rawToken), type, now)
        .orElseThrow(IdentityErrors::actionTokenInvalid);
  }
}
