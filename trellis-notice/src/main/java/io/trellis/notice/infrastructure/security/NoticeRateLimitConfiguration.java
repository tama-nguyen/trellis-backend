package io.trellis.notice.infrastructure.security;

import io.trellis.notice.api.security.NoticeRateLimitPolicies;
import io.trellis.platform.aspect.ratelimit.RateLimitPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class NoticeRateLimitConfiguration {
  @Value("${trellis.rate-limit.policies.notice-create:30/PT1M}")
  private String create;

  @Bean
  RateLimitPolicy noticeCreatePolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        NoticeRateLimitPolicies.CREATE, "actor", value -> value, create);
  }
}
