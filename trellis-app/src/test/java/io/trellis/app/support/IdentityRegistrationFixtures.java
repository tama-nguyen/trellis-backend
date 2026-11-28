package io.trellis.app.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.usecase.registration.RegisterUseCase;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobEntity;
import io.trellis.identity.infrastructure.persistence.mail.IdentityMailJobRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.val;

/** Builds real pending accounts for tests whose subject is delivery, rather than registration. */
@RequiredArgsConstructor
public class IdentityRegistrationFixtures {
  private final RegisterUseCase register;
  private final IdentityMailJobRepository mailJobs;
  private final Clock clock;

  public IdentityApi.Registration registration() {
    return new IdentityApi.Registration(
        UUID.randomUUID() + "@example.test", IdentityFixtures.PASSWORD, "Identity fixture");
  }

  public PendingAccount pendingAccount() {
    val account = registration();
    val existing =
        mailJobs.findAll().stream().map(IdentityMailJobEntity::getId).collect(Collectors.toSet());
    val userId =
        register.execute(context(), account.email(), account.password(), account.displayName());
    val added =
        mailJobs.findAll().stream()
            .map(IdentityMailJobEntity::getId)
            .collect(Collectors.toCollection(HashSet::new));
    added.removeAll(existing);
    assertEquals(1, added.size(), "registration fixture must enqueue one mail job");
    return new PendingAccount(userId, account, added.iterator().next());
  }

  public RequestContext context() {
    return RequestContext.builder()
        .correlationId(UUID.randomUUID().toString())
        .requestTime(clock.instant())
        .build();
  }

  public record PendingAccount(
      Long userId, IdentityApi.Registration registration, Long mailJobId) {}
}
