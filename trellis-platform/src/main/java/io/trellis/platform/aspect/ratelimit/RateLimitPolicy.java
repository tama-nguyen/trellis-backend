package io.trellis.platform.aspect.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.function.UnaryOperator;

public record RateLimitPolicy(
    String id, String subjectType, UnaryOperator<String> normalize, List<Limit> limits) {
  public RateLimitPolicy {
    limits = List.copyOf(limits);
    if (id.isBlank() || limits.isEmpty())
      throw new IllegalArgumentException("Invalid rate limit policy");
  }

  public record Limit(long capacity, Duration period) {
    public Limit {
      if (capacity <= 0 || period.isNegative() || period.isZero())
        throw new IllegalArgumentException("Invalid bandwidth");
    }
  }
}
