package io.trellis.platform.aspect.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

/** In-memory buckets in a bounded Caffeine cache. Idle buckets expire after an hour (a full bucket anyway). */
public final class LocalRateLimiterStore implements RateLimiterStore {
    private static final Duration IDLE_EXPIRY = Duration.ofHours(1);

    private final Cache<String, Bucket> buckets;

    public LocalRateLimiterStore(long maxBuckets) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxBuckets)
                .expireAfterAccess(IDLE_EXPIRY)
                .build();
    }

    @Override
    public Duration tryConsume(String bucketKey, int permits, Duration per) {
        Bucket bucket = buckets.get(
                bucketKey,
                k -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(permits)
                                .refillGreedy(permits, per)
                                .build())
                        .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(probe.getNanosToWaitForRefill());
    }
}
