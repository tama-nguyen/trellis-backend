package io.trellis.notification.infrastructure.security;

import io.trellis.notification.api.security.NotificationRateLimitPolicies;
import io.trellis.platform.aspect.ratelimit.RateLimitPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class NotificationRateLimitConfiguration {
  @Value("${trellis.rate-limit.policies.notification-create:30/PT1M}")
  private String create;

  @Bean
  RateLimitPolicy notificationCreatePolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        NotificationRateLimitPolicies.CREATE, "actor", value -> value, create);
  }
}
