package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.trellis.common.error.ServiceUnavailableException;
import io.trellis.platform.aspect.ratelimit.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RateLimitEngineTest {
  static RateLimitProperties properties() {
    var p = new RateLimitProperties("memory", 1000, new RateLimitProperties.Redis(""));
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
    RateLimiterStore store = mock(RateLimiterStore.class);
    when(store.tryConsume(anyString(), anyList())).thenReturn(Duration.ZERO);
    var engine =
        new RateLimitEngine(
            store, List.of(policy()), properties(), new SimpleMeterRegistry(), Clock.systemUTC());
    engine.consume("login", " User@Example.Test ");
    engine.consume("login", "user@example.test");
    var keys = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(store, times(2)).tryConsume(keys.capture(), anyList());
    assertEquals(keys.getAllValues().getFirst(), keys.getAllValues().getLast());
    assertFalse(keys.getValue().contains("example"));
    assertFalse(keys.getValue().contains("@"));
  }

  @Test
  void invalidSubjectAndUnknownPolicyNeverReachStore() {
    var store = mock(RateLimiterStore.class);
    var engine =
        new RateLimitEngine(
            store, List.of(policy()), properties(), new SimpleMeterRegistry(), Clock.systemUTC());
    assertThrows(IllegalArgumentException.class, () -> engine.consume("missing", "user"));
    assertThrows(IllegalArgumentException.class, () -> engine.consume("login", " "));
    verifyNoInteractions(store);
  }

  @Test
  void outageIs503AndCircuitStopsRepeatedCalls() {
    var store = mock(RateLimiterStore.class);
    when(store.tryConsume(anyString(), anyList())).thenThrow(new IllegalStateException("offline"));
    var engine =
        new RateLimitEngine(
            store,
            List.of(policy()),
            properties(),
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    assertThrows(ServiceUnavailableException.class, () -> engine.consume("login", "user"));
    assertThrows(ServiceUnavailableException.class, () -> engine.consume("login", "user"));
    verify(store).tryConsume(anyString(), anyList());
  }

  @Test
  void multipleBandwidthsConsumeAtomicallyUnderConcurrency() throws Exception {
    var store = new LocalRateLimiterStore(100);
    var limits =
        RateLimitPolicies.parse("test", "actor", value -> value, "20/PT24H,3/PT24H").limits();
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 40; i++) tasks.add(() -> store.tryConsume("same", limits).isZero());
      int allowed = 0;
      for (var result : executor.invokeAll(tasks)) if (result.get()) allowed++;
      assertEquals(3, allowed);
    }
  }
}
