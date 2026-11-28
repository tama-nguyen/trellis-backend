package io.trellis.identity.application.usecase.registration;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.identity.IdentityProperties;
import io.trellis.identity.api.error.IdentityErrors;
import io.trellis.identity.api.security.IdentityAuditActions;
import io.trellis.identity.application.port.PasswordHasher;
import io.trellis.identity.application.port.TokenCodec.SecretToken;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.domain.user.NewUser;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a pending user and transactionally queues the verification link for later delivery. */
@Service
@RequiredArgsConstructor
public class RegisterUseCase {

  private final UserRepository userRepository;
  private final UserMailSender userMailSender;
  private final PasswordHasher passwordHasher;
  private final ActionTokens actionTokens;
  private final IdentityProperties identityProperties;

  @SecurityAudited(SecurityAction.REGISTER)
  @Audited(
      action = IdentityAuditActions.REGISTER,
      aggregateType = IdentityAuditActions.AGGREGATE_TYPE,
      aggregateId = "#result")
  @Transactional
  @Observed(name = "identity.register")
  public Long execute(
      final RequestContext ctx,
      final String email,
      final String password,
      final String displayName) {
    final Instant requestTime = ctx.requestTime();
    final NewUser newUser =
        new NewUser(
            EmailAddress.of(email).value(),
            displayName.trim(),
            UserStatus.PENDING_VERIFICATION,
            null,
            requestTime,
            requestTime);

    final User pendingUser;
    try {
      final String passwordHash = passwordHasher.hash(password);
      pendingUser = userRepository.createUser(newUser, passwordHash);
    } catch (DuplicateKeyException alreadyTaken) {
      // The unique index is the only safe check: testing first would still race another
      // registration.
      throw IdentityErrors.userAlreadyExists(alreadyTaken);
    }

    final SecretToken verificationToken =
        actionTokens.issue(
            pendingUser,
            UserActionType.VERIFY_EMAIL,
            identityProperties.verifyEmailTokenTtl(),
            requestTime);

    userMailSender.sendEmailVerification(
        pendingUser.email(), pendingUser.displayName(), verificationToken.raw());

    return pendingUser.id();
  }
}
