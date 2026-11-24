package io.trellis.platform.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Value;

/**
 * requestLog: log one line per request (method, path, status, duration); never logs query or body
 * correlationIdPattern: accepted inbound X-Correlation-Id; anything else is replaced by a fresh id
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public final class WebProperties {
  @Value("${trellis.web.request-log:true}")
  private boolean requestLog;

  @Value("${trellis.web.correlation-id-pattern:^[A-Za-z0-9][A-Za-z0-9._:-]{7,99}$}")
  private String correlationIdPattern;
}
