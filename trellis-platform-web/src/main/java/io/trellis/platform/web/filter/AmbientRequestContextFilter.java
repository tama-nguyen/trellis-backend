package io.trellis.platform.web.filter;

import io.trellis.common.context.AmbientRequestContext;
import io.trellis.common.context.RequestContext;
import io.trellis.common.logging.LogKeys;
import io.trellis.platform.web.context.RequestContextFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after authentication: binds the request's {@link RequestContext} for edge code (aspects,
 * executors) and puts the actor into the MDC so every log line says who acted.
 */
public final class AmbientRequestContextFilter extends OncePerRequestFilter {
  private final RequestContextFactory factory;

  public AmbientRequestContextFilter(RequestContextFactory factory) {
    this.factory = factory;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    RequestContext ctx = factory.from(request);
    MDC.put(LogKeys.ACTOR, ctx.actor().id());
    MDC.put(LogKeys.ACTOR_TYPE, ctx.actor().type().name());
    try (AmbientRequestContext.Scope scope = AmbientRequestContext.open(ctx)) {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(LogKeys.ACTOR);
      MDC.remove(LogKeys.ACTOR_TYPE);
    }
  }
}
