package io.trellis.identity.application.usecase.mail;

import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.port.IdentityMailQueue;
import io.trellis.identity.application.view.IdentityMailClaim;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClaimIdentityMailUseCase {
  private final IdentityMailQueue identityMailQueue;

  @Transactional
  public Optional<IdentityMailClaim> execute(final RequestContext ctx) {
    return identityMailQueue.claim(ctx.requestTime());
  }
}
