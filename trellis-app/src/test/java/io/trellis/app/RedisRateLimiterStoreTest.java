package io.trellis.app;

import static org.junit.jupiter.api.Assertions.*;

import io.trellis.platform.aspect.ratelimit.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class RedisRateLimiterStoreTest {
  @Test
  void instancesShareAtomicQuotaAndRestartKeepsStateUntilRefill() throws Exception {
    try (var redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)) {
      redis.start();
      String uri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
      var limits =
          RateLimitPolicies.parse("test", "actor", value -> value, "7/PT1H,3/PT1H").limits();
      try (var first = new RedisRateLimiterStore(uri);
          var second = new RedisRateLimiterStore(uri);
          var executor = Executors.newFixedThreadPool(8)) {
        var calls = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 32; i++) {
          var store = i % 2 == 0 ? first : second;
          calls.add(() -> store.tryConsume("shared", limits).isZero());
        }
        int allowed = 0;
        for (var result : executor.invokeAll(calls)) if (result.get()) allowed++;
        assertEquals(3, allowed);
        try (var restarted = new RedisRateLimiterStore(uri)) {
          assertFalse(restarted.tryConsume("shared", limits).isZero());
        }
        String ttl =
            redis.execInContainer("redis-cli", "TTL", "trellis:rl:shared").getStdout().trim();
        assertTrue(Long.parseLong(ttl) >= 3590);
      }
    }
  }
}
