package io.trellis.identity;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public final class IdentityTestFixtures {
  public static final Instant NOW = Instant.parse("2026-11-24T09:30:00Z");
  public static final IdentityProperties PROPERTIES =
      new IdentityProperties(
          Duration.ofMinutes(15),
          Duration.ofDays(30),
          Duration.ofMinutes(30),
          Duration.ofMinutes(15),
          12,
          "learner",
          URI.create("http://localhost/verify-email"),
          "no-reply@example.test");

  private IdentityTestFixtures() {}

  public static RequestContext anonymousContext() {
    return RequestContext.builder().correlationId("test-correlation").requestTime(NOW).build();
  }

  public static RequestContext userContext(final String actorId) {
    return RequestContext.builder()
        .actor(Actor.user(actorId, Set.of(), Set.of()))
        .correlationId("test-correlation")
        .requestTime(NOW)
        .build();
  }

  public static User user(final UserStatus status) {
    return new User(
        1L,
        "learner@example.test",
        "Learner",
        status,
        status == UserStatus.PENDING_VERIFICATION ? null : NOW.minusSeconds(60),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60));
  }
}
