package io.trellis.platform.web.security;

import io.trellis.common.context.RequestContext;
import io.trellis.common.error.PlatformErrorCode;
import io.trellis.common.http.HttpHeaderNames;
import io.trellis.common.id.UuidV7;
import io.trellis.common.logging.LogKeys;
import io.trellis.common.security.SecurityAction;
import io.trellis.platform.security.audit.SecurityEventRecorder;
import io.trellis.platform.web.error.MappedProblem;
import io.trellis.platform.web.error.ProblemMapper;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;

/** Maps Spring Security's exceptions; the detail never says why a token was rejected. */
public final class SecurityProblemMapper implements ProblemMapper {
  private static final String RETRY_AFTER_SECONDS = "5";

  private final SecurityEventRecorder events;
  private final Clock clock;

  public SecurityProblemMapper(SecurityEventRecorder events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  @Override
  public Optional<MappedProblem> map(Throwable error) {
    if (error instanceof AuthenticationException) {
      String correlation = MDC.get(LogKeys.CORRELATION_ID);
      var ctx =
          RequestContext.builder()
              .correlationId(correlation == null ? UuidV7.next(clock).toString() : correlation)
              .requestTime(clock.instant())
              .build();
      events.record(
          ctx,
          SecurityAction.AUTHENTICATE,
          error instanceof AuthenticationServiceException
              ? SecurityEventRecorder.Outcome.ERROR
              : SecurityEventRecorder.Outcome.REJECTED,
          error instanceof AuthenticationServiceException
              ? PlatformErrorCode.SERVICE_UNAVAILABLE.code()
              : PlatformErrorCode.UNAUTHENTICATED.code());
    }
    return switch (error) {
      // The identity provider (JWKS) is unreachable: a retryable 503, never a 401 that logs users
      // out.
      case AuthenticationServiceException e ->
          Optional.of(
              new MappedProblem(
                  PlatformErrorCode.SERVICE_UNAVAILABLE,
                  "Authentication is temporarily unavailable",
                  Map.of(),
                  retryAfter()));
      case AuthenticationException e ->
          Optional.of(
              MappedProblem.of(
                  PlatformErrorCode.UNAUTHENTICATED, "A valid bearer token is required"));
      case AccessDeniedException e ->
          Optional.of(
              MappedProblem.of(
                  PlatformErrorCode.ACCESS_DENIED,
                  "You do not have permission to perform this action"));
      default -> Optional.empty();
    };
  }

  private static HttpHeaders retryAfter() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaderNames.RETRY_AFTER, RETRY_AFTER_SECONDS);
    return headers;
  }
}
