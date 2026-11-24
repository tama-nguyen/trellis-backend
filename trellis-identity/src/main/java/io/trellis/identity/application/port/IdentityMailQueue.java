package io.trellis.identity.application.port;

import io.trellis.identity.application.view.IdentityMailClaim;
import java.time.Instant;
import java.util.Optional;

public interface IdentityMailQueue {
  Optional<IdentityMailClaim> claim(Instant now);

  void complete(IdentityMailClaim claim, Instant now);
}
