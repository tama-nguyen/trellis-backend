package io.trellis.learningcontent.infrastructure.security;

import io.trellis.learningcontent.api.security.CourseRateLimitPolicies;
import io.trellis.platform.aspect.ratelimit.RateLimitPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class CourseRateLimitConfiguration {
  @Value("${trellis.rate-limit.policies.learningcontent-create:30/PT1M}")
  private String create;

  @Bean
  RateLimitPolicy learningcontentCreatePolicy() {
    return io.trellis.platform.aspect.ratelimit.RateLimitPolicies.parse(
        CourseRateLimitPolicies.CREATE, "actor", value -> value, create);
  }
}
