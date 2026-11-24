package io.trellis.identity.application.usecase.registration;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.api.security.IdentityAuditActions;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.action.UserActionToken;
import io.trellis.identity.domain.action.UserActionType;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns a pending registration into one that can sign in. */
@Service
@RequiredArgsConstructor
public class VerifyEmailUseCase {

  private final UserRepository userRepository;
  private final ActionTokens actionTokens;

  @SecurityAudited(SecurityAction.VERIFY_EMAIL)
  @Audited(
      action = IdentityAuditActions.VERIFY_EMAIL,
      aggregateType = IdentityAuditActions.AGGREGATE_TYPE,
      aggregateId = "#result")
  @Transactional
  @Observed(name = "identity.verify-email")
  public UUID execute(final RequestContext ctx, final String rawToken) {
    final Instant requestTime = ctx.requestTime();
    final UserActionToken verificationAction =
        actionTokens.lockActive(rawToken, UserActionType.VERIFY_EMAIL, requestTime);
    final UUID userId = verificationAction.user().id();

    userRepository.verifyEmail(userId, requestTime);
    userRepository.consumeActionToken(verificationAction.id(), requestTime);

    return userId;
  }
}
