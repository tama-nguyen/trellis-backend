package io.trellis.identity.application.usecase.mail;

import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.port.IdentityMailQueue;
import io.trellis.identity.application.view.IdentityMailClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteIdentityMailUseCase {
  private final IdentityMailQueue identityMailQueue;

  @Transactional
  public void execute(final RequestContext ctx, final IdentityMailClaim claim) {
    identityMailQueue.complete(claim, ctx.requestTime());
  }
}
