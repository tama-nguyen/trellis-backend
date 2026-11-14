package io.trellis.platform.aspect.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Shared buckets in Redis via Bucket4j's compare-and-swap proxy; keys expire once the bucket would be full. */
public final class RedisRateLimiterStore implements RateLimiterStore, AutoCloseable {
    private static final String KEY_PREFIX = "trellis:rl:";

    private final RedisClient client;
    private final ProxyManager<byte[]> buckets;

    public RedisRateLimiterStore(String uri) {
        this.client = RedisClient.create(uri);
        this.buckets = Bucket4jLettuce.casBasedBuilder(client)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }

    @Override
    public Duration tryConsume(String bucketKey, int permits, Duration per) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(permits)
                        .refillGreedy(permits, per)
                        .build())
                .build();
        ConsumptionProbe probe = buckets.builder()
                .build((KEY_PREFIX + bucketKey).getBytes(StandardCharsets.UTF_8), () -> config)
                .tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(probe.getNanosToWaitForRefill());
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
