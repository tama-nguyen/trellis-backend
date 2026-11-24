package io.trellis.platform.web.security;

import io.trellis.platform.web.WebAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;

/**
 * HTTP edge of security: the two filter chains, the JWT decoder bean and the mapping of Spring
 * Security exceptions to RFC 7807. Runs after {@link
 * io.trellis.platform.security.SecurityAutoConfiguration} (which owns actors and authorization and
 * has no servlet dependency) and before Spring Boot's own security auto-configuration, so ours is
 * the only filter chain and JWT decoder.
 */
@AutoConfiguration(
    after = io.trellis.platform.security.SecurityAutoConfiguration.class,
    before = {
      org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class,
      ManagementWebSecurityAutoConfiguration.class
    })
@Import(SecurityFilterChains.class)
public class WebSecurityAutoConfiguration {
  @Bean
  @Order(WebAutoConfiguration.TRELLIS_MAPPER_ORDER + 10)
  SecurityProblemMapper securityProblemMapper() {
    return new SecurityProblemMapper();
  }
}
