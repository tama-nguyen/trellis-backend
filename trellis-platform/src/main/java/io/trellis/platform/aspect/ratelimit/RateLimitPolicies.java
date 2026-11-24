package io.trellis.platform.aspect.ratelimit;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.UnaryOperator;

public final class RateLimitPolicies {
  private RateLimitPolicies() {}

  public static RateLimitPolicy parse(
      String id, String subjectType, UnaryOperator<String> normalize, String specification) {
    return new RateLimitPolicy(
        id,
        subjectType,
        normalize,
        Arrays.stream(specification.split(","))
            .map(
                value -> {
                  String[] parts = value.trim().split("/");
                  if (parts.length != 2)
                    throw new IllegalArgumentException("Invalid bandwidth specification");
                  return new RateLimitPolicy.Limit(
                      Long.parseLong(parts[0]), Duration.parse(parts[1]));
                })
            .toList());
  }
}
