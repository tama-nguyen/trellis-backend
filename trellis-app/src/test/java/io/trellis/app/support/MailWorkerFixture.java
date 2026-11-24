package io.trellis.app.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.identity.application.port.IdentityMailDelivery;
import io.trellis.identity.application.usecase.mail.ClaimIdentityMailUseCase;
import io.trellis.identity.application.usecase.mail.CompleteIdentityMailUseCase;
import io.trellis.identity.infrastructure.mail.IdentityMailCipher;
import io.trellis.identity.infrastructure.mail.IdentityMailWorker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MailWorkerFixture {
  private final ClaimIdentityMailUseCase claim;
  private final CompleteIdentityMailUseCase complete;
  private final IdentityMailDelivery delivery;
  private final IdentityMailCipher cipher;
  private final Clock clock;
  private final MeterRegistry meters;

  public IdentityMailWorker worker() {
    return at(clock.instant());
  }

  // Claim and completion use RequestContext time, including their native SQL predicates.
  public IdentityMailWorker at(final Instant now) {
    return new IdentityMailWorker(
        claim, complete, delivery, cipher, Clock.fixed(now, ZoneOffset.UTC), meters);
  }
}
