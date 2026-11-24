package io.trellis.platform.web.security;

import io.trellis.platform.aspect.ratelimit.*;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@Getter
@Accessors(fluent = true)
public class HttpRateLimitConfiguration {
  public static final String AUTH = "http.auth";
  public static final String RECOVERY = "http.recovery";
  public static final String ACTION = "http.action";
  public static final String SESSION = "http.session";

  @Value("${trellis.rate-limit.http.trusted-proxies:}")
  private String trustedProxies;

  @Value("${trellis.rate-limit.http.auth:60/PT1M}")
  private String auth;

  @Value("${trellis.rate-limit.http.recovery:20/PT1M}")
  private String recovery;

  @Value("${trellis.rate-limit.http.action:60/PT1M}")
  private String action;

  @Value("${trellis.rate-limit.http.session:120/PT1M}")
  private String session;

  @Bean
  RateLimitPolicy httpAuthPolicy() {
    return policy(AUTH, auth);
  }

  @Bean
  RateLimitPolicy httpRecoveryPolicy() {
    return policy(RECOVERY, recovery);
  }

  @Bean
  RateLimitPolicy httpActionPolicy() {
    return policy(ACTION, action);
  }

  @Bean
  RateLimitPolicy httpSessionPolicy() {
    return policy(SESSION, session);
  }

  private RateLimitPolicy policy(String id, String quota) {
    return RateLimitPolicies.parse(id, "ip", value -> value, quota);
  }
}
