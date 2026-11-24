package io.trellis.identity.infrastructure.mail;

import io.trellis.common.id.UuidV7;
import io.trellis.identity.IdentityMailProperties;
import io.trellis.identity.application.port.*;
import io.trellis.identity.application.view.IdentityMail;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.EmailAddress;
import io.trellis.identity.infrastructure.persistence.mail.*;
import java.time.Clock;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class QueuedUserMailSender implements UserMailSender {
  private static final String SCOPE_SEPARATOR = ":";
  private final IdentityMailJobRepository jobs;
  private final IdentityMailCipher cipher;
  private final IdentityMailProperties properties;
  private final TokenCodec tokens;
  private final Clock clock;

  public QueuedUserMailSender(
      IdentityMailJobRepository jobs,
      IdentityMailCipher cipher,
      IdentityMailProperties properties,
      TokenCodec tokens,
      Clock clock) {
    this.jobs = jobs;
    this.cipher = cipher;
    this.properties = properties;
    this.tokens = tokens;
    this.clock = clock;
  }

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
    var job = new IdentityMailJobEntity();
    job.setId(UuidV7.next(clock));
    job.setActionHash(actionHash);
    job.setKeyVersion(properties.keyVersion());
    job.setPayload(cipher.encrypt(mail));
    job.setNextAttemptAt(clock.instant());
    job.setCreatedAt(clock.instant());
    jobs.save(job);
  }
}
