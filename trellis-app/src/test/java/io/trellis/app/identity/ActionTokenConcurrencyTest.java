package io.trellis.app.identity;

import static io.trellis.app.support.IdentityFixtures.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.trellis.app.support.IdentityFixtures;
import io.trellis.app.support.SecurityTestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.identity.application.support.ActionTokens;
import io.trellis.identity.application.usecase.registration.VerifyEmailUseCase;
import io.trellis.identity.domain.action.UserActionType;
import io.trellis.identity.domain.user.User;
import io.trellis.identity.domain.user.UserStatus;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenEntity;
import io.trellis.identity.infrastructure.persistence.action.UserActionTokenJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;

@SecurityTestContext
@RequiredArgsConstructor
class ActionTokenConcurrencyTest {

  private static final Duration ACTION_TTL = Duration.ofMinutes(5);
  private static final int PARTICIPANTS = 2;
  private static final int TIMEOUT_SECONDS = 20;
  private final UserRepository users;
  private final ActionTokens actions;
  private final VerifyEmailUseCase verifyEmail;
  private final IdentityFixtures fixtures;
  private final UserActionTokenJpaRepository actionTokens;

  @Test
  void concurrentIssuanceLeavesOneOutstandingAction() throws Exception {
    // Given
    val user = pendingUser();
    val barrier = new CyclicBarrier(PARTICIPANTS);
    val issue = issueTogether(user, barrier);

    // When
    val issued = concurrently(issue);

    // Then
    for (val raw : issued) {
      assertNotNull(raw);
    }
    val owned = new UserActionTokenEntity();
    owned.setUserId(user.id());
    val persisted = actionTokens.findAll(Example.of(owned));
    val outstanding = persisted.stream().filter(token -> token.getConsumedAt() == null).count();
    assertEquals(1L, outstanding);
  }

  @Test
  void concurrentConsumptionVerifiesUserOnlyOnce() throws Exception {
    // Given
    val user = pendingUser();
    val raw = issueAction(user);
    val barrier = new CyclicBarrier(PARTICIPANTS);
    val consume = consumeTogether(raw, barrier);

    // When
    val outcomes = concurrently(consume);

    // Then
    val successes = outcomes.stream().filter(Boolean::booleanValue).count();
    assertEquals(1L, successes);
    assertEquals(UserStatus.ACTIVE, users.userById(user.id()).orElseThrow().status());
  }

  private User pendingUser() {
    val id = fixtures.nativeUser(UserStatus.PENDING_VERIFICATION);
    return users.userById(id).orElseThrow();
  }

  private String issueAction(final User user) {
    return fixtures
        .tx()
        .execute(
            status ->
                actions.issue(user, UserActionType.VERIFY_EMAIL, ACTION_TTL, Instant.now()).raw());
  }

  private Callable<String> issueTogether(final User user, final CyclicBarrier barrier) {
    return () -> {
      barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return issueAction(user);
    };
  }

  private Callable<Boolean> consumeTogether(final String raw, final CyclicBarrier barrier) {
    return () -> {
      barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return consume(raw);
    };
  }

  private boolean consume(final String raw) {
    try {
      verifyEmail.execute(context(), raw);
      return true;
    } catch (final TrellisException rejected) {
      return false;
    }
  }

  private <T> List<T> concurrently(final Callable<T> operation) throws Exception {
    try (val executor = Executors.newFixedThreadPool(PARTICIPANTS)) {
      val futures =
          executor.invokeAll(List.of(operation, operation), TIMEOUT_SECONDS, TimeUnit.SECONDS);
      val outcomes = new ArrayList<T>();
      for (val future : futures) {
        outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
      }
      return outcomes;
    }
  }
}
