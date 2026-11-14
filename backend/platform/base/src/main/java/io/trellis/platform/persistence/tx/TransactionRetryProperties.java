package io.trellis.platform.persistence.tx;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retry policy for {@link RetryingTransactions}.
 *
 * @param maxAttempts total attempts including the first
 * @param baseBackoff backoff ceiling of the first retry
 * @param maxBackoff cap of the backoff ceiling
 */
@ConfigurationProperties(prefix = "trellis.persistence.tx-retry")
public record TransactionRetryProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("PT0.05S") Duration baseBackoff,
        @DefaultValue("PT1S") Duration maxBackoff) {}
