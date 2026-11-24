package io.trellis.platform.persistence.tx;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * Retry policy for {@link RetryingTransactions}.
 *
 * <p>maxAttempts: total attempts including the first baseBackoff: backoff ceiling of the first
 * retry maxBackoff: cap of the backoff ceiling
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class TransactionRetryProperties {
  @Value("${trellis.persistence.tx-retry.max-attempts:3}")
  private int maxAttempts;

  @Value("${trellis.persistence.tx-retry.base-backoff:PT0.05S}")
  private Duration baseBackoff;

  @Value("${trellis.persistence.tx-retry.max-backoff:PT1S}")
  private Duration maxBackoff;
}
