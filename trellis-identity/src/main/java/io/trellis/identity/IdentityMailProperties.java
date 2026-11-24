package io.trellis.identity;

import java.time.Duration;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

@Getter
@Accessors(fluent = true)
public class IdentityMailProperties {
  @Value("${trellis.identity.mail.key-version:v1}")
  private String keyVersion;

  @Value("${trellis.identity.mail.encryption-key}")
  private String encryptionKey;

  @Value("${trellis.identity.mail.lease:PT1M}")
  private Duration lease;

  @Value("${trellis.identity.mail.retry-delay:PT30S}")
  private Duration retryDelay;

  @Value("${trellis.identity.mail.max-attempts:5}")
  private int maxAttempts;
}
