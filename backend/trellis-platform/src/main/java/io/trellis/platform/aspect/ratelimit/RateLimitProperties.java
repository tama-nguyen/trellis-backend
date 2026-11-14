package io.trellis.platform.aspect.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param store {@code memory} (per instance) or {@code redis} (shared)
 * @param cacheSize max buckets kept in memory; least recently used are evicted
 * @param redis Redis connection, used when {@code store=redis}
 */
@ConfigurationProperties(prefix = "trellis.rate-limit")
public record RateLimitProperties(
        @DefaultValue("memory") String store,
        @DefaultValue("100000") long cacheSize,
        @DefaultValue Redis redis) {
    public static final String STORE_REDIS = "redis";

    /** @param uri e.g. {@code redis://:password@host:6379/0}; sensitive because it may hold the password */
    public record Redis(
            @DefaultValue("redis://localhost:6379") String uri) {}
}
