package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.trellis.common.error.ServiceUnavailableException;
import io.trellis.platform.aspect.ratelimit.LocalRateLimiterStore;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import io.trellis.platform.aspect.ratelimit.RateLimitPolicies;
import io.trellis.platform.aspect.ratelimit.RateLimitPolicy;
import io.trellis.platform.aspect.ratelimit.RateLimitProperties;
import io.trellis.platform.aspect.ratelimit.RateLimiterStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitEngineTest {
  static RateLimitProperties properties() {
    val p = new RateLimitProperties("memory", 1000, new RateLimitProperties.Redis(""));
    ReflectionTestUtils.setField(p, "keySecret", "test-only-secret");
    ReflectionTestUtils.setField(p, "namespace", "test");
    ReflectionTestUtils.setField(p, "revision", "v1");
    return p;
  }

  static RateLimitPolicy policy() {
    return RateLimitPolicies.parse(
        "login", "email", value -> value.strip().toLowerCase(Locale.ROOT), "1/PT1H");
  }

  @Test
  void canonicalVariantsSharePrivateBucket() {
    // Given
    val store = mock(RateLimiterStore.class);
    when(store.tryConsume(anyString(), anyList())).thenReturn(Duration.ZERO);
    val engine =
        new RateLimitEngine(
            store, List.of(policy()), properties(), new SimpleMeterRegistry(), Clock.systemUTC());

    // When
    engine.consume("login", " User@Example.Test ");
    engine.consume("login", "user@example.test");
    val keys = ArgumentCaptor.forClass(String.class);

    // Then
    verify(store, times(2)).tryConsume(keys.capture(), anyList());
    assertEquals(keys.getAllValues().getFirst(), keys.getAllValues().getLast());
    assertFalse(keys.getValue().contains("example"));
    assertFalse(keys.getValue().contains("@"));
  }

  @Test
  void unknownPolicyNeverReachesStore() {
    // Given
    val store = mock(RateLimiterStore.class);
    val engine =
        new RateLimitEngine(
            store, List.of(policy()), properties(), new SimpleMeterRegistry(), Clock.systemUTC());

    // When
    assertThrows(IllegalArgumentException.class, () -> engine.consume("missing", "user"));

    // Then
    verifyNoInteractions(store);
  }

  @Test
  void invalidSubjectNeverReachesStore() {
    // Given
    val store = mock(RateLimiterStore.class);
    val engine =
        new RateLimitEngine(
            store, List.of(policy()), properties(), new SimpleMeterRegistry(), Clock.systemUTC());

    // When
    // Then
    assertThrows(IllegalArgumentException.class, () -> engine.consume("login", " "));
    verifyNoInteractions(store);
  }

  @Test
  void outageIs503AndCircuitStopsRepeatedCalls() {
    // Given
    val store = mock(RateLimiterStore.class);
    when(store.tryConsume(anyString(), anyList())).thenThrow(new IllegalStateException("offline"));
    val engine =
        new RateLimitEngine(
            store,
            List.of(policy()),
            properties(),
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    // When
    assertThrows(ServiceUnavailableException.class, () -> engine.consume("login", "user"));

    // Then
    assertThrows(ServiceUnavailableException.class, () -> engine.consume("login", "user"));
    verify(store).tryConsume(anyString(), anyList());
  }

  @Test
  void multipleBandwidthsConsumeAtomicallyUnderConcurrency() throws Exception {
    // Given
    val store = new LocalRateLimiterStore(100);
    val limits =
        RateLimitPolicies.parse("test", "actor", value -> value, "20/PT24H,3/PT24H").limits();

    // When & Then
    try (val executor = Executors.newFixedThreadPool(8)) {
      val tasks = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 40; i++) {
        tasks.add(() -> store.tryConsume("same", limits).isZero());
      }
      int allowed = 0;
      for (val result : executor.invokeAll(tasks)) {
        if (result.get()) {
          allowed++;
        }
      }
      assertEquals(3, allowed);
    }
  }
}
