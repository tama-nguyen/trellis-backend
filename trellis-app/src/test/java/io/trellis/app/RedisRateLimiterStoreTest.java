package io.trellis.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.trellis.platform.aspect.ratelimit.RateLimitPolicies;
import io.trellis.platform.aspect.ratelimit.RedisRateLimiterStore;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class RedisRateLimiterStoreTest {
  @Test
  void instancesShareAtomicQuotaAndRestartKeepsStateUntilRefill() throws Exception {
    try (val redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)) {
      redis.start();
      val uri = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
      val limits =
          RateLimitPolicies.parse("test", "actor", value -> value, "7/PT1H,3/PT1H").limits();
      try (val first = new RedisRateLimiterStore(uri);
          val second = new RedisRateLimiterStore(uri);
          val executor = Executors.newFixedThreadPool(8)) {
        val calls = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < 32; i++) {
          val store = i % 2 == 0 ? first : second;
          calls.add(() -> store.tryConsume("shared", limits).isZero());
        }
        int allowed = 0;
        for (val result : executor.invokeAll(calls)) {
          if (result.get()) {
            allowed++;
          }
        }
        assertEquals(3, allowed);
        try (val restarted = new RedisRateLimiterStore(uri)) {
          assertFalse(restarted.tryConsume("shared", limits).isZero());
        }
        val ttl = redis.execInContainer("redis-cli", "TTL", "trellis:rl:shared").getStdout().trim();
        assertTrue(Long.parseLong(ttl) >= 3590);
      }
    }
  }
}
