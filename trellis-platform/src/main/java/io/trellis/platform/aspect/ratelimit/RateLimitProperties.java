package io.trellis.platform.aspect.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * store: {@code memory} (per instance) or {@code redis} (shared) cacheSize: max buckets kept in
 * memory; least recently used are evicted redis: Redis connection, used when {@code store=redis}
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
public final class RateLimitProperties {
  @Value("${trellis.rate-limit.store:memory}")
  private String store;

  @Value("${trellis.rate-limit.cache-size:100000}")
  private long cacheSize;

  @Value("${trellis.rate-limit.redis.uri:redis://localhost:6379}")
  private String redisUri;

  public RateLimitProperties(String store, long cacheSize, Redis redis) {
    this.store = store;
    this.cacheSize = cacheSize;
    this.redisUri = redis.uri();
  }

  public Redis redis() {
    return new Redis(redisUri);
  }

  public static final String STORE_REDIS = "redis";

  /** uri: e.g. {@code redis://:password@host:6379/0}; sensitive because it may hold the password */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Redis {
    private final String uri;
  }
}
