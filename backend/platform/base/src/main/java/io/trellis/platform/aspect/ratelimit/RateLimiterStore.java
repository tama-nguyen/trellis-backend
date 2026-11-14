package io.trellis.platform.aspect.ratelimit;

import java.time.Duration;

/** Bucket storage. */
public interface RateLimiterStore {
    /** Takes one token; returns {@link Duration#ZERO} when allowed, else the wait until the next token. */
    Duration tryConsume(String bucketKey, int permits, Duration per);
}
