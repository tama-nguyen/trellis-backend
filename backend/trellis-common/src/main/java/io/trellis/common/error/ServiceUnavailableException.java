package io.trellis.common.error;

import java.time.Duration;
import java.util.Optional;

/** A dependency is down; the request may succeed later. Distinct from 401 so clients do not log users out. */
public class ServiceUnavailableException extends TrellisException implements RetryAfterAware {
    private final Duration retryAfter;

    public ServiceUnavailableException(String message, Duration retryAfter, Throwable cause) {
        super(PlatformErrorCode.SERVICE_UNAVAILABLE, message, cause);
        this.retryAfter = retryAfter;
    }

    @Override
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
