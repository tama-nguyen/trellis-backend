package io.trellis.platform.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxUriTags cap of distinct {@code uri} tag values on HTTP server metrics; beyond it new values are
 *     dropped so a path-parameter bug cannot explode Prometheus cardinality
 */
@ConfigurationProperties(prefix = "trellis.observability")
public record ObservabilityProperties(@DefaultValue("200") int maxUriTags) {}
