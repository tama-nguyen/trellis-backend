package io.trellis.app.support;

import io.trellis.app.PostgresTestcontainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
@Import({
  PostgresTestcontainer.class,
  TestApiClient.class,
  IdentityFixtures.class,
  IdentityApi.class,
  IdentityRegistrationFixtures.class,
  MailWorkerFixture.class,
  AuditProbe.class,
  RoleFixtures.class
})
public class SecurityTestConfiguration {
  @Bean
  @Primary
  JwtDecoder testDecoder() throws Exception {
    return TestJwtIssuer.decoder();
  }
}
