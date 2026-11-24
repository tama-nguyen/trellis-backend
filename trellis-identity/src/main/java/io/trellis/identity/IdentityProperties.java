package io.trellis.identity;

import java.net.URI;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/** Native user, session and user-action settings. */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class IdentityProperties {

  @Value("${trellis.identity.access-token-ttl:PT15M}")
  private Duration accessTokenTtl;

  @Value("${trellis.identity.refresh-token-ttl:P30D}")
  private Duration refreshTokenTtl;

  @Value("${trellis.identity.verify-email-token-ttl:PT30M}")
  private Duration verifyEmailTokenTtl;

  @Value("${trellis.identity.reset-password-code-ttl:PT15M}")
  private Duration resetPasswordCodeTtl;

  @Value("${trellis.identity.bcrypt-strength:12}")
  private int bcryptStrength;

  @Value("${trellis.identity.default-role:learner}")
  private String defaultRole;

  @Value("${trellis.identity.verify-email-uri:http://localhost:13000/verify-email}")
  private URI verifyEmailUri;

  @Value("${trellis.identity.mail-from:no-reply@example.test}")
  private String mailFrom;
}
