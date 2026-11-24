package io.trellis.platform.aspect.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.List;

/** Bounded development store. Eviction or process restart resets quota. */
public final class LocalRateLimiterStore implements RateLimiterStore {

  private final Cache<String, Bucket> buckets;

  public LocalRateLimiterStore(long maxBuckets) {
    this.buckets = Caffeine.newBuilder().maximumSize(maxBuckets).build();
  }

  @Override
  public Duration tryConsume(String bucketKey, List<RateLimitPolicy.Limit> limits) {
    Bucket bucket =
        buckets.get(
            bucketKey,
            k -> {
              var builder = Bucket.builder();
              limits.forEach(
                  limit ->
                      builder.addLimit(
                          Bandwidth.builder()
                              .capacity(limit.capacity())
                              .refillGreedy(limit.capacity(), limit.period())
                              .build()));
              return builder.build();
            });
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(probe.getNanosToWaitForRefill());
  }
}
