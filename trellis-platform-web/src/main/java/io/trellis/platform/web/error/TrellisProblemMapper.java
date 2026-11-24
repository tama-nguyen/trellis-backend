package io.trellis.platform.web.error;

import io.trellis.common.error.ErrorCategory;
import io.trellis.common.error.ProblemFields;
import io.trellis.common.error.RetryAfterAware;
import io.trellis.common.error.TrellisException;
import io.trellis.common.error.ValidationException;
import io.trellis.common.http.HttpHeaderNames;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/** Maps the platform's own exception hierarchy. */
public final class TrellisProblemMapper implements ProblemMapper {
  /** Floor for Retry-After so clients never spin on "0". */
  private static final long MIN_RETRY_AFTER_SECONDS = 1;

  @Override
  public Optional<MappedProblem> map(Throwable error) {
    if (!(error instanceof TrellisException te)) {
      return Optional.empty();
    }
    Map<String, Object> properties = new LinkedHashMap<>(te.properties());
    if (te instanceof ValidationException ve) {
      properties.put(ProblemFields.ERRORS, ve.violations());
    }
    HttpHeaders headers = new HttpHeaders();
    if (te instanceof RetryAfterAware aware) {
      aware
          .retryAfter()
          .ifPresent(d -> headers.set(HttpHeaderNames.RETRY_AFTER, retryAfterSeconds(d)));
    }
    if (te.errorCode().category() == ErrorCategory.RATE_LIMITED
        && !headers.containsHeader(HttpHeaderNames.RETRY_AFTER)) {
      headers.set(HttpHeaderNames.RETRY_AFTER, String.valueOf(MIN_RETRY_AFTER_SECONDS));
    }
    return Optional.of(new MappedProblem(te.errorCode(), te.getMessage(), properties, headers));
  }

  static String retryAfterSeconds(Duration duration) {
    long seconds = duration.getSeconds() + (duration.getNano() == 0 ? 0 : 1);
    return String.valueOf(Math.max(MIN_RETRY_AFTER_SECONDS, seconds));
  }
}
