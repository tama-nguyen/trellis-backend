package io.trellis.identity.application.usecase.password;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.security.IdentityAuditActions;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mails a reset code, and says nothing about whether it did.
 *
 * <p>An unknown address, a disabled user and a failed send all end the same way, because the
 * response is otherwise an oracle for which addresses are registered.
 */
@Service
@RequiredArgsConstructor
public class ForgotPasswordUseCase {

  private final UserRepository userRepository;
  private final UserMailSender userMailSender;
  private final PasswordResetCodes passwordResetCodes;
  private final IdentityProperties identityProperties;

  @SecurityAudited(SecurityAction.FORGOT_PASSWORD)
  @Audited(
      action = IdentityAuditActions.FORGOT_PASSWORD,
      aggregateType = IdentityAuditActions.AGGREGATE_TYPE)
  @Transactional
  @Observed(name = "identity.request-password-reset")
  public void execute(final RequestContext ctx, final String email) {
    final EmailAddress emailAddress = EmailAddress.of(email);
    final Optional<User> registeredUser = userRepository.userByEmail(emailAddress);

    registeredUser.ifPresent(user -> sendResetCode(user, ctx.requestTime()));
  }

  private void sendResetCode(final User user, final Instant now) {
    if (user.status() == UserStatus.DISABLED) {
      return;
    }

    final String code =
        passwordResetCodes.issue(user, identityProperties.resetPasswordCodeTtl(), now);

    userMailSender.sendPasswordResetCode(user.email(), user.displayName(), code);
  }
}
