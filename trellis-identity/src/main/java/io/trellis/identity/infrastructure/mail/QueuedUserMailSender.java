package io.trellis.identity.infrastructure.mail;

import io.trellis.identity.IdentityMailProperties;
import io.trellis.identity.application.port.TokenCodec;
import io.trellis.identity.application.port.UserMailSender;
import io.trellis.identity.application.view.IdentityMail;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobEntity;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class QueuedUserMailSender implements UserMailSender {
  private static final String SCOPE_SEPARATOR = ":";
  private final IdentityMailJobRepository jobs;
  private final IdentityMailCipher cipher;
  private final IdentityMailProperties properties;
  private final TokenCodec tokens;
  private final Clock clock;

  @Override
  public void sendEmailVerification(String email, String displayName, String token) {
    enqueue(
        new IdentityMail(UserActionType.VERIFY_EMAIL, email, displayName, token),
        tokens.hash(token));
  }

  @Override
  public void sendPasswordResetCode(String email, String displayName, String code) {
    enqueue(
        new IdentityMail(UserActionType.RESET_PASSWORD, email, displayName, code),
        tokens.hash(EmailAddress.of(email).value() + SCOPE_SEPARATOR + code));
  }

  private void enqueue(IdentityMail mail, String actionHash) {
    IdentityMailJobEntity job = new IdentityMailJobEntity();
    job.setActionHash(actionHash);
    job.setKeyVersion(properties.keyVersion());
    job.setPayload(cipher.encrypt(mail));
    job.setNextAttemptAt(clock.instant());
    job.setCreatedAt(clock.instant());
    jobs.save(job);
  }
}
