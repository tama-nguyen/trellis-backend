package io.trellis.identity.application.support;

import io.trellis.common.id.ReferenceCode;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues and validates human-readable, single-use password reset codes scoped to an email address.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetCodes {
  static final String PREFIX = "PW";
  private static final String SCOPE_SEPARATOR = ":";

  private final UserRepository users;
  private final TokenCodec tokens;
  private final ReferenceCodeGenerator generator;
  private final Clock clock;

  public String issue(User user, Duration ttl, Instant now) {
    String code = generator.next().value();
    users.createActionToken(
        UuidV7.next(clock),
        user.id(),
        UserActionType.RESET_PASSWORD,
        hash(user.email(), code),
        now.plus(ttl),
        now);
    return code;
  }

  public UserActionToken lockActive(String email, String rawCode, Instant now) {
    EmailAddress canonicalEmail = EmailAddress.of(email);
    ReferenceCode code =
        ReferenceCode.parse(PREFIX, rawCode).orElseThrow(IdentityErrors::actionTokenInvalid);
    return users
        .lockActiveActionToken(
            hash(canonicalEmail.value(), code.value()), UserActionType.RESET_PASSWORD, now)
        .orElseThrow(IdentityErrors::actionTokenInvalid);
  }

  private String hash(String email, String code) {
    return tokens.hash(email + SCOPE_SEPARATOR + code);
  }
}
