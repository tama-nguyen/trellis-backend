package io.trellis.common.error;

import java.time.Duration;
import java.util.Optional;

/**
 * An error that tells the client when to try again; the web layer writes the Retry-After header.
 */
public interface RetryAfterAware {
  Optional<Duration> retryAfter();
}
