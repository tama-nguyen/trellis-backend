package io.trellis.identity.application.support;

import io.trellis.common.id.ReferenceCode;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
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

  private final UserRepository userRepository;
  private final TokenCodec tokenCodec;
  private final ReferenceCodeGenerator resetCodeGenerator;

  public String issue(final User user, final Duration ttl, final Instant now) {
    String code = resetCodeGenerator.next().value();
    userRepository.createActionToken(
        user.id(), UserActionType.RESET_PASSWORD, hash(user.email(), code), now.plus(ttl), now);
    return code;
  }

  public UserActionToken lockActive(final String email, final String rawCode, final Instant now) {
    final EmailAddress canonicalEmail = EmailAddress.of(email);
    final ReferenceCode code =
        ReferenceCode.parse(PREFIX, rawCode).orElseThrow(IdentityErrors::actionTokenInvalid);
    return userRepository
        .lockActiveActionToken(
            hash(canonicalEmail.value(), code.value()), UserActionType.RESET_PASSWORD, now)
        .orElseThrow(IdentityErrors::actionTokenInvalid);
  }

  private String hash(final String email, final String code) {
    return tokenCodec.hash(email + SCOPE_SEPARATOR + code);
  }
}
