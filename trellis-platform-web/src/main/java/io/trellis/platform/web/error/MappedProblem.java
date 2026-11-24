package io.trellis.platform.web.error;

import io.trellis.common.error.ErrorCode;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * A throwable translated into client terms.
 *
 * @param errorCode decides status, title and {@code code}
 * @param detail client-visible explanation; replaced by a generic text for 5xx
 * @param properties extra problem members (e.g. {@code violations}); must be safe to show
 * @param headers extra response headers (Allow, Retry-After)
 */
public record MappedProblem(
    ErrorCode errorCode, String detail, Map<String, Object> properties, HttpHeaders headers) {
  public MappedProblem {
    properties = Map.copyOf(properties);
  }

  public static MappedProblem of(ErrorCode errorCode, String detail) {
    return new MappedProblem(errorCode, detail, Map.of(), new HttpHeaders());
  }
}
