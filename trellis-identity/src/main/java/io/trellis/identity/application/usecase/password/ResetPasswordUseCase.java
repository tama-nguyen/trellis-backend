package io.trellis.identity.application.usecase.password;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.api.security.IdentityAuditActions;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.PasswordResetCodes;
import io.trellis.identity.domain.action.UserActionToken;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets a new password and ends every session, because the old one may be how the password was lost.
 */
@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final PasswordResetCodes passwordResetCodes;

  @SecurityAudited(SecurityAction.RESET_PASSWORD)
  @Audited(
      action = IdentityAuditActions.RESET_PASSWORD,
      aggregateType = IdentityAuditActions.AGGREGATE_TYPE,
      aggregateId = "#result")
  @Transactional
  @Observed(name = "identity.reset-password")
  public Long execute(
      final RequestContext ctx, final String email, final String code, final String newPassword) {
    final Instant requestTime = ctx.requestTime();
    final UserActionToken passwordResetAction =
        passwordResetCodes.lockActive(email, code, requestTime);
    final Long userId = passwordResetAction.user().id();
    final String passwordHash = passwordHasher.hash(newPassword);

    userRepository.changePassword(userId, passwordHash, requestTime);
    userRepository.revokeSessions(userId, requestTime);
    userRepository.consumeActionToken(passwordResetAction.id(), requestTime);

    return userId;
  }
}
