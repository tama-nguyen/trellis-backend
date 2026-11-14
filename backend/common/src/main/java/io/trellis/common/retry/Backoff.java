package io.trellis.common.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Exponential backoff with full jitter and a cap, identical in Java and Python ({@code trellis_core.backoff}):
 *
 * <pre>
 * ceilingMs(attempt) = min(maxMs, floor(baseMs * multiplier ^ attempt))   // exponent clamped at 62
 * delayMs            = floor(uniform * ceilingMs),  uniform in [0, 1)
 * </pre>
 *
 * Attempt 0 is the first retry. Full jitter spreads many clients that failed together (a broker restart) better
 * than equal jitter; the price is that a retry can come almost immediately, which is fine for idempotent work.
 * Shared vectors: {@code contracts/testdata/backoff.json}.
 *
 * @param baseDelay ceiling of attempt 0
 * @param multiplier growth per attempt, at least 1
 * @param maxDelay cap of the ceiling
 */
public record Backoff(Duration baseDelay, double multiplier, Duration maxDelay) {
    /** Cross-language defaults: 1 s, doubling, capped at 30 s. */
    public static final Backoff DEFAULT = new Backoff(Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30));

    private static final int MAX_EXPONENT = 62;
    private static final double DEFAULT_MULTIPLIER = 2.0;

    public Backoff {
        Objects.requireNonNull(baseDelay, "baseDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive");
        }
        if (maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
    }

    /** Doubling backoff. */
    public Backoff(Duration baseDelay, Duration maxDelay) {
        this(baseDelay, DEFAULT_MULTIPLIER, maxDelay);
    }

    /** Upper bound of the delay for {@code attempt}, in milliseconds, without jitter. */
    public long ceilingMillis(int attempt) {
        int exponent = Math.clamp(attempt, 0, MAX_EXPONENT);
        double raw = Math.floor(baseDelay.toMillis() * Math.pow(multiplier, exponent));
        return raw >= maxDelay.toMillis() ? maxDelay.toMillis() : (long) raw;
    }

    /** Jittered delay for {@code attempt} given a {@code uniform} draw in [0, 1); deterministic for tests. */
    public long delayMillis(int attempt, double uniform) {
        if (uniform < 0.0 || uniform >= 1.0) {
            throw new IllegalArgumentException("uniform must be in [0, 1)");
        }
        return (long) Math.floor(uniform * ceilingMillis(attempt));
    }

    public Duration delay(int attempt, RandomGenerator random) {
        return Duration.ofMillis(delayMillis(attempt, random.nextDouble()));
    }

    public Duration delay(int attempt) {
        return delay(attempt, ThreadLocalRandom.current());
    }
}
