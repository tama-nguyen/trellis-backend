package io.trellis.platform.aspect.ratelimit;

import java.time.Duration;
import java.util.List;

/** Bucket storage. */
public interface RateLimiterStore {
  /**
   * Takes one token; returns {@link Duration#ZERO} when allowed, else the wait until the next
   * token.
   */
  Duration tryConsume(String bucketKey, List<RateLimitPolicy.Limit> limits);

  default Duration tryConsume(String bucketKey, int permits, Duration per) {
    return tryConsume(bucketKey, List.of(new RateLimitPolicy.Limit(permits, per)));
  }
}
