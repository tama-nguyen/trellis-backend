package io.trellis.platform.observability;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * maxUriTags: cap of distinct {@code uri} tag values on HTTP server metrics; beyond it new values
 * are dropped so a path-parameter bug cannot explode Prometheus cardinality
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class ObservabilityProperties {
  @Value("${trellis.observability.max-uri-tags:200}")
  private int maxUriTags;
}
