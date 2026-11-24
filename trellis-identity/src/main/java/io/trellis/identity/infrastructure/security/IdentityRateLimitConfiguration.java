package io.trellis.identity.infrastructure.security;

import io.trellis.identity.api.security.IdentityRateLimitPolicies;
import io.trellis.platform.aspect.ratelimit.RateLimitPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class IdentityRateLimitConfiguration {
  @Value("${trellis.rate-limit.policies.identity-login:5/PT1M}")
  private String login;

  @Bean
  RateLimitPolicy identityLoginPolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        IdentityRateLimitPolicies.LOGIN,
        "email",
        value -> io.trellis.identity.domain.user.EmailAddress.of(value).value(),
        login);
  }

  @Value("${trellis.rate-limit.policies.identity-register:3/PT15M}")
  private String register;

  @Bean
  RateLimitPolicy identityRegisterPolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        IdentityRateLimitPolicies.REGISTER,
        "email",
        value -> io.trellis.identity.domain.user.EmailAddress.of(value).value(),
        register);
  }

  @Value("${trellis.rate-limit.policies.identity-forgot:1/PT1M,3/PT1H}")
  private String forgot;

  @Bean
  RateLimitPolicy identityForgotPolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        IdentityRateLimitPolicies.FORGOT,
        "email",
        value -> io.trellis.identity.domain.user.EmailAddress.of(value).value(),
        forgot);
  }

  @Value("${trellis.rate-limit.policies.identity-reset:5/PT15M}")
  private String reset;

  @Bean
  RateLimitPolicy identityResetPolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        IdentityRateLimitPolicies.RESET,
        "email",
        value -> io.trellis.identity.domain.user.EmailAddress.of(value).value(),
        reset);
  }
}
