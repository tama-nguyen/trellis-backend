package io.trellis.common.error;

import java.time.Duration;
import java.util.Optional;

/** A rate limit was hit; {@link #retryAfter()} becomes the Retry-After header. */
public class RateLimitedException extends TrellisException implements RetryAfterAware {
    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(PlatformErrorCode.RATE_LIMITED, "Rate limit exceeded");
        this.retryAfter = retryAfter;
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.of(retryAfter);
    }
}
