package io.trellis.identity;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

public final class IdentityTestFixtures {
  public static final Instant NOW = Instant.parse("2026-11-24T09:30:00Z");
  public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
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

  public static RequestContext userContext(String actorId) {
    return RequestContext.builder()
        .actor(Actor.user(actorId, Set.of(), Set.of()))
        .correlationId("test-correlation")
        .requestTime(NOW)
        .build();
  }

  public static User user(UserStatus status) {
    return new User(
        UUID.fromString("019abf42-9410-7000-8000-000000000001"),
        "learner@example.test",
        "Learner",
        status,
        status == UserStatus.PENDING_VERIFICATION ? null : NOW.minusSeconds(60),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60));
  }
}
