package io.trellis.identity.infrastructure.mail;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.port.IdentityMailDelivery;
import io.trellis.identity.application.usecase.mail.ClaimIdentityMailUseCase;
import io.trellis.identity.application.usecase.mail.CompleteIdentityMailUseCase;
import io.trellis.identity.application.view.IdentityMail;
import io.trellis.platform.persistence.ConditionalOnServingApp;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "trellis.identity.mail",
    name = "worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnServingApp
@RequiredArgsConstructor
public class IdentityMailWorker {
  private static final String COMPONENT = "identity-mail";
  private final ClaimIdentityMailUseCase claimIdentityMailUseCase;
  private final CompleteIdentityMailUseCase completeIdentityMailUseCase;
  private final IdentityMailDelivery identityMailDelivery;
  private final IdentityMailCipher identityMailCipher;
  private final Clock clock;
  private final MeterRegistry meterRegistry;

  @Scheduled(fixedDelayString = "${trellis.identity.mail.poll-interval:PT5S}")
  public void tick() {
    claimIdentityMailUseCase
        .execute(RequestContext.system(COMPONENT, clock))
        .ifPresent(
            job -> {
              try {
                final IdentityMail mail =
                    identityMailCipher.decrypt(job.keyVersion(), job.payload());

                switch (mail.type()) {
                  case VERIFY_EMAIL ->
                      identityMailDelivery.sendEmailVerification(
                          mail.email(), mail.displayName(), mail.secret());

                  case RESET_PASSWORD ->
                      identityMailDelivery.sendPasswordResetCode(
                          mail.email(), mail.displayName(), mail.secret());
                }

                completeIdentityMailUseCase.execute(RequestContext.system(COMPONENT, clock), job);
                meterRegistry.counter("trellis.identity.mail", "outcome", "sent").increment();

              } catch (RuntimeException failure) {
                // Exception messages may include mail addresses or bodies. Only record bounded
                // outcome metrics.
                meterRegistry.counter("trellis.identity.mail", "outcome", "retry").increment();
              }
            });
  }
}
