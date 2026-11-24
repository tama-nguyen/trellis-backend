package io.trellis.platform.aspect.idempotency;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * inProgressTimeout: how long a claim blocks the key if the process dies mid-call; after it a retry
 * may run again cleanupCron: when expired rows are deleted
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class IdempotencyProperties {
  @Value("${trellis.idempotency.in-progress-timeout:PT5M}")
  private Duration inProgressTimeout;

  @Value("${trellis.idempotency.cleanup-cron:0 17 * * * *}")
  private String cleanupCron;
}
