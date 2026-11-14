package io.trellis.platform.aspect.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param inProgressTimeout how long a claim blocks the key if the process dies mid-call; after it a retry may
 *     run again
 * @param cleanupCron when expired rows are deleted
 */
@ConfigurationProperties(prefix = "trellis.idempotency")
public record IdempotencyProperties(
        @DefaultValue("PT5M") Duration inProgressTimeout,
        @DefaultValue("0 17 * * * *") String cleanupCron) {}
