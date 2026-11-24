package io.trellis.platform.aspect.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.trellis.common.error.ServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RateLimitEngine {
  private static final String HMAC = "HmacSHA256";
  private static final Duration RECOVERY = Duration.ofSeconds(5);
  private final RateLimiterStore store;
  private final Map<String, RateLimitPolicy> policies;
  private final RateLimitProperties properties;
  private final MeterRegistry meters;
  private final Clock clock;
  private volatile Instant unavailableUntil = Instant.MIN;

  public RateLimitEngine(
      RateLimiterStore store,
      List<RateLimitPolicy> policies,
      RateLimitProperties properties,
      MeterRegistry meters,
      Clock clock) {
    this.store = store;
    this.properties = properties;
    this.meters = meters;
    this.clock = clock;
    this.policies =
        policies.stream()
            .collect(Collectors.toUnmodifiableMap(RateLimitPolicy::id, Function.identity()));
    if (properties.keySecret().isBlank())
      throw new IllegalArgumentException("Rate limit key secret is required");
  }

  public void requirePolicy(String policyId) {
    if (!policies.containsKey(policyId))
      throw new IllegalArgumentException("Unknown rate limit policy: " + policyId);
  }

  public Duration consume(String policyId, String subject) {
    RateLimitPolicy policy = policies.get(policyId);
    if (policy == null) throw new IllegalArgumentException("Unknown rate limit policy");
    if (subject == null || subject.isBlank())
      throw new IllegalArgumentException("Missing rate limit subject");
    String canonical = policy.normalize().apply(subject);
    if (canonical == null || canonical.isBlank())
      throw new IllegalArgumentException("Missing canonical subject");
    String key =
        properties.namespace()
            + ":"
            + properties.revision()
            + ":"
            + policy.id()
            + ":"
            + digest(policy.subjectType() + "\0" + canonical);
    if (clock.instant().isBefore(unavailableUntil)) throw unavailable(null);
    try {
      Duration wait = store.tryConsume(key, policy.limits());
      meters
          .counter(
              "trellis.rate.limit",
              "policy",
              policyId,
              "layer",
              policy.subjectType().equals("ip") ? "http" : "operation",
              "outcome",
              wait.isZero() ? "allowed" : "exceeded",
              "store",
              properties.store())
          .increment();
      return wait;
    } catch (RuntimeException failure) {
      unavailableUntil = clock.instant().plus(RECOVERY);
      meters
          .counter(
              "trellis.rate.limit",
              "policy",
              policyId,
              "layer",
              policy.subjectType().equals("ip") ? "http" : "operation",
              "outcome",
              "store_error",
              "store",
              properties.store())
          .increment();
      throw unavailable(failure);
    }
  }

  private ServiceUnavailableException unavailable(Throwable cause) {
    return new ServiceUnavailableException("Rate limit service unavailable", RECOVERY, cause);
  }

  private String digest(String subject) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(properties.keySecret().getBytes(StandardCharsets.UTF_8), HMAC));
      return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("HMAC unavailable", failure);
    }
  }
}
