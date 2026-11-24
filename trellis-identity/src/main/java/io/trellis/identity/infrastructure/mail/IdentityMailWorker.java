package io.trellis.identity.infrastructure.mail;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.port.IdentityMailDelivery;
import io.trellis.identity.application.usecase.mail.*;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "trellis.identity.mail",
    name = "worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdentityMailWorker {
  private static final String COMPONENT = "identity-mail";
  private final ClaimIdentityMailUseCase claim;
  private final CompleteIdentityMailUseCase complete;
  private final IdentityMailDelivery delivery;
  private final IdentityMailCipher cipher;
  private final Clock clock;
  private final MeterRegistry meters;

  public IdentityMailWorker(
      ClaimIdentityMailUseCase claim,
      CompleteIdentityMailUseCase complete,
      IdentityMailDelivery delivery,
      IdentityMailCipher cipher,
      Clock clock,
      MeterRegistry meters) {
    this.claim = claim;
    this.complete = complete;
    this.delivery = delivery;
    this.cipher = cipher;
    this.clock = clock;
    this.meters = meters;
  }

  @Scheduled(fixedDelayString = "${trellis.identity.mail.poll-interval:PT5S}")
  public void tick() {
    claim
        .execute(RequestContext.system(COMPONENT, clock))
        .ifPresent(
            job -> {
              try {
                var mail = cipher.decrypt(job.keyVersion(), job.payload());
                switch (mail.type()) {
                  case VERIFY_EMAIL ->
                      delivery.sendEmailVerification(
                          mail.email(), mail.displayName(), mail.secret());
                  case RESET_PASSWORD ->
                      delivery.sendPasswordResetCode(
                          mail.email(), mail.displayName(), mail.secret());
                }
                complete.execute(RequestContext.system(COMPONENT, clock), job);
                meters.counter("trellis.identity.mail", "outcome", "sent").increment();
              } catch (RuntimeException failure) {
                // Exception messages may include mail addresses or bodies. Only record bounded
                // outcome metrics.
                meters.counter("trellis.identity.mail", "outcome", "retry").increment();
              }
            });
  }
}
