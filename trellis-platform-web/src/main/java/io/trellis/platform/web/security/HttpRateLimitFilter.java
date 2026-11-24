package io.trellis.platform.web.security;

import io.trellis.common.error.RateLimitedException;
import io.trellis.common.error.ServiceUnavailableException;
import io.trellis.platform.aspect.ratelimit.RateLimitEngine;
import io.trellis.platform.web.error.ProblemResponseWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

public final class HttpRateLimitFilter extends OncePerRequestFilter {
  private static final String POST = "POST";
  private static final Map<String, String> ROUTES =
      Map.of(
          "/api/auth/login", HttpRateLimitConfiguration.AUTH,
          "/api/auth/register", HttpRateLimitConfiguration.AUTH,
          "/api/auth/forgot-password", HttpRateLimitConfiguration.RECOVERY,
          "/api/auth/reset-password", HttpRateLimitConfiguration.RECOVERY,
          "/api/auth/verify-email", HttpRateLimitConfiguration.ACTION,
          "/api/auth/refresh", HttpRateLimitConfiguration.SESSION,
          "/api/auth/logout", HttpRateLimitConfiguration.SESSION);
  private final RateLimitEngine engine;
  private final ClientAddressResolver addresses;
  private final ProblemResponseWriter problems;

  public HttpRateLimitFilter(
      RateLimitEngine engine, ClientAddressResolver addresses, ProblemResponseWriter problems) {
    this.engine = engine;
    this.addresses = addresses;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String policy = POST.equals(request.getMethod()) ? ROUTES.get(request.getServletPath()) : null;
    if (policy != null) {
      try {
        Duration wait = engine.consume(policy, addresses.resolve(request));
        if (!wait.isZero()) throw new RateLimitedException(wait);
      } catch (RateLimitedException | ServiceUnavailableException error) {
        problems.write(request, response, error);
        return;
      } catch (IllegalArgumentException error) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
